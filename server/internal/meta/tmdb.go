package meta

import (
	"context"
	"fmt"
	"net/url"
	"strings"
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
	IMDBID       string  `json:"imdb_id"`
	Genres       []struct {
		Name string `json:"name"`
	} `json:"genres"`
}

func (e *Enricher) tmdbEnabled() bool {
	return strings.TrimSpace(e.tmdbKey) != ""
}

func (e *Enricher) overlayTMDB(ctx context.Context, kind string, info Info, title string, year int) Info {
	if !e.tmdbEnabled() {
		return info
	}
	var movie tmdbMovie
	var err error
	if info.ImdbID != "" {
		movie, err = e.tmdbFind(ctx, kind, info.ImdbID)
	}
	if err != nil || movie.ID == 0 {
		movie, err = e.tmdbSearch(ctx, kind, title, year)
	}
	if err != nil || movie.ID == 0 {
		return info
	}
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
	if kind == "episode" && len(wrap.TVResults) > 0 {
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
	if kind == "episode" {
		path = "search/tv"
	}
	u := fmt.Sprintf("https://api.themoviedb.org/3/%s?query=%s&api_key=%s", path, url.QueryEscape(q), url.QueryEscape(e.tmdbKey))
	if year > 0 && kind != "episode" {
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
	for _, r := range wrap.Results {
		name := strings.ToLower(strings.TrimSpace(r.Title + " " + r.Name))
		y := yearFromRelease(r.ReleaseDate)
		if y == 0 {
			y = yearFromRelease(r.FirstAirDate)
		}
		if strings.Contains(name, want) && (year == 0 || y == 0 || y == year) {
			return r, nil
		}
	}
	return wrap.Results[0], nil
}

func (e *Enricher) tmdbDetail(ctx context.Context, kind string, id int) (tmdbMovie, error) {
	path := "movie"
	if kind == "episode" {
		path = "tv"
	}
	u := fmt.Sprintf("https://api.themoviedb.org/3/%s/%d?api_key=%s", path, id, url.QueryEscape(e.tmdbKey))
	var movie tmdbMovie
	if err := e.getJSON(ctx, u, &movie); err != nil {
		return tmdbMovie{}, err
	}
	return movie, nil
}
