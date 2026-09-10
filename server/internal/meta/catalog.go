package meta

import (
	"context"
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
)

type CatalogItem struct {
	ID             string       `json:"id"`
	Kind           string       `json:"kind"`
	Title          string       `json:"title"`
	Year           int          `json:"year,omitempty"`
	Plot           string       `json:"plot,omitempty"`
	PosterURL      string       `json:"posterUrl,omitempty"`
	BackdropURL    string       `json:"backdropUrl,omitempty"`
	ImdbID         string       `json:"imdbId,omitempty"`
	MediaID        string       `json:"mediaId,omitempty"`
	InLibrary      bool         `json:"inLibrary"`
	Rating         float64      `json:"rating,omitempty"`
	Genres         []string     `json:"genres,omitempty"`
	ShowTitle      string       `json:"showTitle,omitempty"`
	Season         int          `json:"season,omitempty"`
	Episode        int          `json:"episode,omitempty"`
	ReleasePhase   string       `json:"releasePhase,omitempty"`
	TMDBID         int          `json:"tmdbId,omitempty"`
	Cast           []CastMember `json:"cast,omitempty"`
	Director       *CastMember  `json:"director,omitempty"`
	RuntimeMinutes int          `json:"runtimeMinutes,omitempty"`
	Certification  string       `json:"certification,omitempty"`
	Country        string       `json:"country,omitempty"`
}

type CastMember struct {
	TMDBID     int    `json:"tmdbId"`
	Name       string `json:"name"`
	Character  string `json:"character,omitempty"`
	ProfileURL string `json:"profileUrl,omitempty"`
}

type Person struct {
	TMDBID             int           `json:"tmdbId"`
	Name               string        `json:"name"`
	ProfileURL         string        `json:"profileUrl,omitempty"`
	KnownForDepartment string        `json:"knownForDepartment,omitempty"`
	Biography          string        `json:"biography,omitempty"`
	Birthday           string        `json:"birthday,omitempty"`
	PlaceOfBirth       string        `json:"placeOfBirth,omitempty"`
	Credits            []CatalogItem `json:"credits,omitempty"`
}

type SearchResult struct {
	Movies []CatalogItem `json:"movies"`
	Series []CatalogItem `json:"series"`
	People []Person      `json:"people"`
}

type catalogCache struct {
	mu      sync.Mutex
	movies  []CatalogItem
	series  []CatalogItem
	fetched time.Time
}

func (c *catalogCache) get() (movies, series []CatalogItem, ok bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	if time.Since(c.fetched) > 15*time.Minute || (len(c.movies) == 0 && len(c.series) == 0) {
		return nil, nil, false
	}
	return append([]CatalogItem(nil), c.movies...), append([]CatalogItem(nil), c.series...), true
}

func (c *catalogCache) set(movies, series []CatalogItem) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.movies = movies
	c.series = series
	c.fetched = time.Now()
}

var homeCatalog = &catalogCache{}

func (e *Enricher) HomeCatalog(ctx context.Context) (movies, series []CatalogItem, err error) {
	if m, s, ok := homeCatalog.get(); ok {
		return m, s, nil
	}
	if e.tmdbEnabled() {
		movies, _ = e.tmdbTrending(ctx, "movie")
		series, _ = e.tmdbTrending(ctx, "tv")
	}
	if len(movies) == 0 {
		movies, _ = e.cinemetaTop(ctx, "movie")
	}
	if len(series) == 0 {
		series, _ = e.cinemetaTop(ctx, "series")
	}
	homeCatalog.set(movies, series)
	return movies, series, nil
}

func (e *Enricher) CatalogShow(ctx context.Context, imdb string) (CatalogItem, []CatalogItem, error) {
	info, _ := e.cinemetaByIMDB(ctx, "episode", imdb)
	cover := CatalogItem{
		ID:          "catalog:" + imdb,
		Kind:        "series",
		Title:       imdb,
		Plot:        info.Plot,
		PosterURL:   info.PosterURL,
		BackdropURL: info.BackdropURL,
		ImdbID:      imdb,
		Rating:      info.Rating,
		Genres:      info.Genres,
		Year:        info.Year,
	}
	var wrap struct {
		Meta struct {
			cinemetaMeta
			Videos []struct {
				ID       string `json:"id"`
				Title    string `json:"title"`
				Name     string `json:"name"`
				Season   int    `json:"season"`
				Episode  int    `json:"episode"`
				Released string `json:"released"`
			} `json:"videos"`
		} `json:"meta"`
	}
	u := "https://v3-cinemeta.strem.io/meta/series/" + url.PathEscape(imdb) + ".json"
	if err := e.getJSON(ctx, u, &wrap); err != nil {
		return cover, nil, err
	}
	if wrap.Meta.Name != "" {
		cover.Title = wrap.Meta.Name
	}
	if wrap.Meta.Description != "" {
		cover.Plot = wrap.Meta.Description
	}
	if wrap.Meta.Poster != "" {
		cover.PosterURL = wrap.Meta.Poster
	}
	if wrap.Meta.Background != "" {
		cover.BackdropURL = wrap.Meta.Background
	}
	eps := []CatalogItem{}
	for _, v := range wrap.Meta.Videos {
		if v.Season <= 0 || v.Episode <= 0 {
			continue
		}
		title := strings.TrimSpace(v.Title)
		if title == "" {
			title = strings.TrimSpace(v.Name)
		}
		if title == "" {
			title = fmt.Sprintf("S%02dE%02d", v.Season, v.Episode)
		}
		eps = append(eps, CatalogItem{
			ID:          fmt.Sprintf("catalog:%s:%d:%d", imdb, v.Season, v.Episode),
			Kind:        "episode",
			Title:       title,
			ShowTitle:   cover.Title,
			Season:      v.Season,
			Episode:     v.Episode,
			Year:        yearFromRelease(v.Released),
			PosterURL:   cover.PosterURL,
			BackdropURL: cover.BackdropURL,
			ImdbID:      imdb,
		})
	}
	return cover, eps, nil
}

func (e *Enricher) tmdbTrending(ctx context.Context, media string) ([]CatalogItem, error) {
	var wrap struct {
		Results []tmdbMovie `json:"results"`
	}
	u := fmt.Sprintf("https://api.themoviedb.org/3/trending/%s/day?api_key=%s", media, url.QueryEscape(e.tmdbKey))
	if err := e.getJSON(ctx, u, &wrap); err != nil {
		return nil, err
	}
	kind := "movie"
	if media == "tv" {
		kind = "series"
	}
	type pending struct {
		item    CatalogItem
		primary string
		status  string
	}
	work := []pending{}
	for _, row := range wrap.Results {
		if len(work) >= 20 {
			break
		}
		item := catalogFromTMDB(row, kind)
		item.TMDBID = row.ID
		if item.ImdbID == "" {
			item.ImdbID = e.tmdbExternalIMDB(ctx, media, row.ID)
		}
		if item.ImdbID == "" {
			continue
		}
		item.ID = "catalog:" + item.ImdbID
		work = append(work, pending{item: item, primary: row.ReleaseDate, status: row.Status})
	}
	if kind == "movie" {
		var wg sync.WaitGroup
		for i := range work {
			if work[i].item.TMDBID == 0 {
				continue
			}
			wg.Add(1)
			go func(i int) {
				defer wg.Done()
				work[i].item.ReleasePhase = e.tmdbMovieReleasePhase(ctx, work[i].item.TMDBID, work[i].primary, work[i].status)
			}(i)
		}
		wg.Wait()
	}
	out := make([]CatalogItem, 0, len(work))
	for _, row := range work {
		out = append(out, row.item)
	}
	return out, nil
}

func (e *Enricher) tmdbExternalIMDB(ctx context.Context, media string, id int) string {
	if id == 0 {
		return ""
	}
	path := "movie"
	if media == "tv" {
		path = "tv"
	}
	var wrap struct {
		IMDBID string `json:"imdb_id"`
	}
	u := fmt.Sprintf("https://api.themoviedb.org/3/%s/%d/external_ids?api_key=%s", path, id, url.QueryEscape(e.tmdbKey))
	if e.getJSON(ctx, u, &wrap) != nil {
		return ""
	}
	if strings.HasPrefix(wrap.IMDBID, "tt") {
		return wrap.IMDBID
	}
	return ""
}

func catalogFromTMDB(row tmdbMovie, kind string) CatalogItem {
	title := strings.TrimSpace(row.Title)
	if title == "" {
		title = strings.TrimSpace(row.Name)
	}
	year := yearFromRelease(row.ReleaseDate)
	if year == 0 {
		year = yearFromRelease(row.FirstAirDate)
	}
	item := CatalogItem{
		Kind:   kind,
		Title:  title,
		Year:   year,
		Plot:   strings.TrimSpace(row.Overview),
		ImdbID: row.IMDBID,
		Rating: row.VoteAverage,
		TMDBID: row.ID,
	}
	if row.PosterPath != "" {
		item.PosterURL = "https://image.tmdb.org/t/p/w500" + row.PosterPath
	}
	if row.BackdropPath != "" {
		item.BackdropURL = "https://image.tmdb.org/t/p/w1280" + row.BackdropPath
	}
	if item.PosterURL == "" && item.ImdbID != "" {
		item.PosterURL = metahubPoster(item.ImdbID)
	}
	if item.BackdropURL == "" && item.ImdbID != "" {
		item.BackdropURL = metahubBackdrop(item.ImdbID)
	}
	applyOverviewMeta(&item, row)
	return item
}

func (e *Enricher) cinemetaTop(ctx context.Context, typ string) ([]CatalogItem, error) {
	var wrap cinemetaSearch
	u := "https://v3-cinemeta.strem.io/catalog/" + typ + "/top.json"
	if err := e.getJSON(ctx, u, &wrap); err != nil {
		return nil, err
	}
	kind := "movie"
	if typ == "series" {
		kind = "series"
	}
	out := []CatalogItem{}
	for _, m := range wrap.Metas {
		if !strings.HasPrefix(m.ID, "tt") {
			continue
		}
		if len(out) >= 20 {
			break
		}
		item := CatalogItem{
			ID:          "catalog:" + m.ID,
			Kind:        kind,
			Title:       m.Name,
			Year:        yearFromRelease(m.ReleaseInfo),
			Plot:        m.Description,
			PosterURL:   m.Poster,
			BackdropURL: m.Background,
			ImdbID:      m.ID,
			Genres:      m.Genres,
		}
		if item.PosterURL == "" {
			item.PosterURL = metahubPoster(m.ID)
		}
		if item.BackdropURL == "" {
			item.BackdropURL = metahubBackdrop(m.ID)
		}
		if r, err := strconv.ParseFloat(m.IMDBRating, 64); err == nil {
			item.Rating = r
		}
		out = append(out, item)
	}
	return out, nil
}
