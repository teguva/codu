package meta

import (
	"context"
	"fmt"
	"net/url"
	"strings"
	"time"
)

type tmdbFind struct {
	MovieResults []tmdbMovie `json:"movie_results"`
	TVResults    []tmdbMovie `json:"tv_results"`
}

type tmdbSearch struct {
	Results []tmdbMovie `json:"results"`
}

type tmdbMovie struct {
	ID           int     `json:"id"`
	Title        string  `json:"title"`
	Name         string  `json:"name"`
	Overview     string  `json:"overview"`
	Tagline      string  `json:"tagline"`
	ReleaseDate  string  `json:"release_date"`
	FirstAirDate string  `json:"first_air_date"`
	PosterPath   string  `json:"poster_path"`
	BackdropPath string  `json:"backdrop_path"`
	VoteAverage  float64 `json:"vote_average"`
	Popularity   float64 `json:"popularity"`
	IMDBID       string  `json:"imdb_id"`
	Status       string  `json:"status"`
	MediaType    string  `json:"media_type"`
	ProfilePath  string  `json:"profile_path"`
	KnownForDept string  `json:"known_for_department"`
	Genres       []struct {
		Name string `json:"name"`
	} `json:"genres"`
	Credits             tmdbCredits        `json:"credits"`
	ReleaseDates        tmdbReleaseDates   `json:"release_dates"`
	ContentRatings      tmdbContentRatings `json:"content_ratings"`
	Runtime             int                `json:"runtime"`
	EpisodeRunTime      []int              `json:"episode_run_time"`
	OriginCountry       []string           `json:"origin_country"`
	ProductionCountries []struct {
		ISO31661 string `json:"iso_3166_1"`
		Name     string `json:"name"`
	} `json:"production_countries"`
}

type tmdbContentRatings struct {
	Results []struct {
		ISO31661 string `json:"iso_3166_1"`
		Rating   string `json:"rating"`
	} `json:"results"`
}

type tmdbCredits struct {
	Cast []tmdbCast `json:"cast"`
	Crew []tmdbCrew `json:"crew"`
}

type tmdbCrew struct {
	ID          int    `json:"id"`
	Name        string `json:"name"`
	Job         string `json:"job"`
	Department  string `json:"department"`
	ProfilePath string `json:"profile_path"`
}

type tmdbCast struct {
	ID          int     `json:"id"`
	Name        string  `json:"name"`
	Character   string  `json:"character"`
	ProfilePath string  `json:"profile_path"`
	Order       int     `json:"order"`
	Popularity  float64 `json:"popularity"`
}

func (e *Enricher) tmdbEnabled() bool {
	return strings.TrimSpace(e.tmdbKey) != ""
}

func (e *Enricher) TMDBEnabled() bool {
	return e.tmdbEnabled()
}

func (e *Enricher) overlayTMDB(ctx context.Context, kind string, info Info, title string, year int) Info {
	if !e.tmdbEnabled() {
		return info
	}
	// Only enrich by confirmed IMDB id. Title search must never invent a match.
	if info.ImdbID == "" {
		return info
	}
	movie, err := e.tmdbFind(ctx, kind, info.ImdbID)
	if err != nil || movie.ID == 0 {
		return info
	}
	_ = title
	_ = year
	detail, err := e.tmdbDetail(ctx, kind, movie.ID)
	if err == nil && detail.ID != 0 {
		movie = detail
	}
	if movie.Tagline != "" {
		info.Tagline = strings.TrimSpace(movie.Tagline)
	}
	if info.Plot == "" {
		info.Plot = strings.TrimSpace(movie.Overview)
	}
	if movie.VoteAverage > 0 && info.Rating <= 0 {
		info.Rating = movie.VoteAverage
	}
	if y := yearFromRelease(movie.ReleaseDate); y > 0 && info.Year == 0 {
		info.Year = y
	}
	if y := yearFromRelease(movie.FirstAirDate); y > 0 && info.Year == 0 {
		info.Year = y
	}
	if movie.IMDBID != "" && info.ImdbID == "" {
		info.ImdbID = movie.IMDBID
	}
	if len(movie.Genres) > 0 && len(info.Genres) == 0 {
		for _, g := range movie.Genres {
			if g.Name != "" {
				info.Genres = append(info.Genres, g.Name)
			}
		}
	}
	if movie.PosterPath != "" {
		info.PosterURL = "https://image.tmdb.org/t/p/w500" + movie.PosterPath
	}
	if movie.BackdropPath != "" {
		info.BackdropURL = "https://image.tmdb.org/t/p/w1280" + movie.BackdropPath
	}
	if info.Source == "" {
		info.Source = "tmdb"
	} else if !strings.Contains(info.Source, "tmdb") {
		info.Source += "+tmdb"
	}
	return info
}

func (e *Enricher) tmdbFind(ctx context.Context, kind, imdb string) (tmdbMovie, error) {
	u := fmt.Sprintf("https://api.themoviedb.org/3/find/%s?external_source=imdb_id&api_key=%s", url.PathEscape(imdb), url.QueryEscape(e.tmdbKey))
	var wrap tmdbFind
	if err := e.getJSON(ctx, u, &wrap); err != nil {
		return tmdbMovie{}, err
	}
	if (kind == "episode" || kind == "series") && len(wrap.TVResults) > 0 {
		return wrap.TVResults[0], nil
	}
	if len(wrap.MovieResults) > 0 {
		return wrap.MovieResults[0], nil
	}
	if len(wrap.TVResults) > 0 {
		return wrap.TVResults[0], nil
	}
	return tmdbMovie{}, fmt.Errorf("tmdb find empty")
}

func (e *Enricher) tmdbSearch(ctx context.Context, kind, title string, year int) (tmdbMovie, error) {
	q := CleanQuery(title)
	path := "search/movie"
	if kind == "episode" || kind == "series" {
		path = "search/tv"
	}
	u := fmt.Sprintf("https://api.themoviedb.org/3/%s?query=%s&api_key=%s", path, url.QueryEscape(q), url.QueryEscape(e.tmdbKey))
	if year > 0 && kind != "episode" && kind != "series" {
		u += fmt.Sprintf("&year=%d", year)
	}
	var wrap tmdbSearch
	if err := e.getJSON(ctx, u, &wrap); err != nil {
		return tmdbMovie{}, err
	}
	if len(wrap.Results) == 0 {
		return tmdbMovie{}, fmt.Errorf("tmdb search empty")
	}
	want := strings.ToLower(q)
	var exact []tmdbMovie
	for _, r := range wrap.Results {
		name := strings.ToLower(strings.TrimSpace(r.Title))
		if name == "" {
			name = strings.ToLower(strings.TrimSpace(r.Name))
		}
		if name != want {
			continue
		}
		y := yearFromRelease(r.ReleaseDate)
		if y == 0 {
			y = yearFromRelease(r.FirstAirDate)
		}
		if year > 0 && y > 0 && y != year {
			continue
		}
		exact = append(exact, r)
	}
	if year > 0 {
		for _, r := range exact {
			y := yearFromRelease(r.ReleaseDate)
			if y == 0 {
				y = yearFromRelease(r.FirstAirDate)
			}
			if y == year {
				return r, nil
			}
		}
		return tmdbMovie{}, fmt.Errorf("tmdb no exact year match")
	}
	if len(exact) == 1 {
		return exact[0], nil
	}
	return tmdbMovie{}, fmt.Errorf("tmdb no exact title match")
}

func (e *Enricher) tmdbDetail(ctx context.Context, kind string, id int) (tmdbMovie, error) {
	path := "movie"
	append := "credits,release_dates"
	if kind == "episode" || kind == "series" {
		path = "tv"
		append = "credits,content_ratings"
	}
	u := fmt.Sprintf("https://api.themoviedb.org/3/%s/%d?api_key=%s&append_to_response=%s", path, id, url.QueryEscape(e.tmdbKey), append)
	var movie tmdbMovie
	if err := e.getJSON(ctx, u, &movie); err != nil {
		return tmdbMovie{}, err
	}
	return movie, nil
}

func (e *Enricher) tmdbMovieReleasePhase(ctx context.Context, id int, primary, status string) string {
	if id == 0 {
		return ClassifyMovieReleasePhase(status, primary, "", "", time.Time{})
	}
	u := fmt.Sprintf("https://api.themoviedb.org/3/movie/%d/release_dates?api_key=%s", id, url.QueryEscape(e.tmdbKey))
	var payload tmdbReleaseDates
	if err := e.getJSON(ctx, u, &payload); err != nil {
		return ClassifyMovieReleasePhase(status, primary, "", "", time.Time{})
	}
	thea, dig := extractMovieReleaseMilestones(payload)
	return ClassifyMovieReleasePhase(status, primary, thea, dig, time.Time{})
}

func tmdbImage(path, size string) string {
	path = strings.TrimSpace(path)
	if path == "" {
		return ""
	}
	if !strings.HasPrefix(path, "/") {
		path = "/" + path
	}
	return "https://image.tmdb.org/t/p/" + size + path
}

func (e *Enricher) Similar(ctx context.Context, kind, imdb string) ([]CatalogItem, error) {
	if !e.tmdbEnabled() {
		return nil, fmt.Errorf("TMDB is not configured")
	}
	imdb = strings.TrimSpace(imdb)
	if !strings.HasPrefix(imdb, "tt") {
		return nil, fmt.Errorf("imdb id required")
	}
	movie, err := e.tmdbFind(ctx, kind, imdb)
	if err != nil || movie.ID == 0 {
		return nil, fmt.Errorf("title not found")
	}
	path := "movie"
	outKind := "movie"
	if kind == "series" || kind == "episode" {
		path = "tv"
		outKind = "series"
	}
	u := fmt.Sprintf("https://api.themoviedb.org/3/%s/%d/similar?api_key=%s", path, movie.ID, url.QueryEscape(e.tmdbKey))
	var wrap tmdbSearch
	if err := e.getJSON(ctx, u, &wrap); err != nil {
		return nil, err
	}
	out := make([]CatalogItem, 0, 16)
	for _, row := range wrap.Results {
		if row.ID == 0 {
			continue
		}
		out = append(out, e.searchItem(row, outKind))
		if len(out) >= 16 {
			break
		}
	}
	return out, nil
}

func creditsFromTMDB(credits tmdbCredits) []CastMember {
	out := []CastMember{}
	for _, row := range credits.Cast {
		if strings.TrimSpace(row.Name) == "" {
			continue
		}
		out = append(out, CastMember{
			TMDBID:     row.ID,
			Name:       row.Name,
			Character:  row.Character,
			ProfileURL: tmdbImage(row.ProfilePath, "w185"),
		})
		if len(out) >= 16 {
			break
		}
	}
	return out
}

func applyOverviewMeta(item *CatalogItem, movie tmdbMovie) {
	if item.RuntimeMinutes == 0 {
		if movie.Runtime > 0 {
			item.RuntimeMinutes = movie.Runtime
		} else if len(movie.EpisodeRunTime) > 0 {
			item.RuntimeMinutes = movie.EpisodeRunTime[0]
		}
	}
	if item.Country == "" {
		item.Country = countryFromTMDB(movie)
	}
	if item.Certification == "" {
		item.Certification = certificationFromTMDB(movie)
	}
	if item.Director == nil {
		item.Director = directorFromTMDB(movie.Credits)
	}
}

func countryFromTMDB(movie tmdbMovie) string {
	if len(movie.OriginCountry) > 0 && strings.TrimSpace(movie.OriginCountry[0]) != "" {
		return displayCountry(movie.OriginCountry[0])
	}
	if len(movie.ProductionCountries) > 0 {
		code := strings.TrimSpace(movie.ProductionCountries[0].ISO31661)
		if code != "" {
			return displayCountry(code)
		}
	}
	return ""
}

func displayCountry(code string) string {
	switch strings.ToUpper(strings.TrimSpace(code)) {
	case "US", "USA":
		return "USA"
	case "GB", "UK":
		return "UK"
	default:
		return strings.ToUpper(strings.TrimSpace(code))
	}
}

func certificationFromTMDB(movie tmdbMovie) string {
	best := ""
	for _, row := range movie.ReleaseDates.Results {
		for _, entry := range row.ReleaseDates {
			cert := strings.TrimSpace(entry.Certification)
			if cert == "" {
				continue
			}
			if strings.EqualFold(row.ISO31661, "US") {
				return cert
			}
			if best == "" {
				best = cert
			}
		}
	}
	for _, row := range movie.ContentRatings.Results {
		cert := strings.TrimSpace(row.Rating)
		if cert == "" {
			continue
		}
		if strings.EqualFold(row.ISO31661, "US") {
			return cert
		}
		if best == "" {
			best = cert
		}
	}
	return best
}

func directorFromTMDB(credits tmdbCredits) *CastMember {
	for _, row := range credits.Crew {
		if !strings.EqualFold(strings.TrimSpace(row.Job), "Director") {
			continue
		}
		name := strings.TrimSpace(row.Name)
		if name == "" {
			continue
		}
		return &CastMember{
			TMDBID:     row.ID,
			Name:       name,
			Character:  "Director",
			ProfileURL: tmdbImage(row.ProfilePath, "w185"),
		}
	}
	return nil
}
