package meta

import (
	"context"
	"fmt"
	"net/url"
	"strconv"
	"strings"
)

type cinemetaSearch struct {
	Metas []cinemetaMeta `json:"metas"`
}

type cinemetaDetail struct {
	Meta cinemetaMeta `json:"meta"`
}

type cinemetaMeta struct {
	ID          string   `json:"id"`
	Type        string   `json:"type"`
	Name        string   `json:"name"`
	Description string   `json:"description"`
	ReleaseInfo string   `json:"releaseInfo"`
	IMDBRating  string   `json:"imdbRating"`
	Runtime     string   `json:"runtime"`
	Genres      []string `json:"genres"`
	Poster      string   `json:"poster"`
	Background  string   `json:"background"`
	Logo        string   `json:"logo"`
}

func (e *Enricher) cinemetaByIMDB(ctx context.Context, kind, imdb string) (Info, error) {
	typ := cinemetaType(kind)
	var wrap cinemetaDetail
	u := "https://v3-cinemeta.strem.io/meta/" + typ + "/" + imdb + ".json"
	if err := e.getJSON(ctx, u, &wrap); err != nil {
		return Info{}, err
	}
	if wrap.Meta.ID == "" && wrap.Meta.Name == "" {
		return Info{}, fmt.Errorf("cinemeta empty for %s", imdb)
	}
	return infoFromCinemeta(wrap.Meta, imdb), nil
}

func (e *Enricher) cinemetaSearch(ctx context.Context, kind, title string, year int) (Info, error) {
	q := CleanQuery(title)
	if q == "" {
		return Info{}, fmt.Errorf("empty title")
	}
	typ := cinemetaType(kind)
	u := "https://v3-cinemeta.strem.io/catalog/" + typ + "/top/search=" + url.PathEscape(q) + ".json"
	var wrap cinemetaSearch
	if err := e.getJSON(ctx, u, &wrap); err != nil {
		return Info{}, err
	}
	picked := pickCinemeta(wrap.Metas, q, year)
	if picked.ID == "" {
		return Info{}, fmt.Errorf("cinemeta no match for %q", q)
	}
	if !strings.HasPrefix(picked.ID, "tt") {
		return Info{}, fmt.Errorf("cinemeta id not imdb: %s", picked.ID)
	}
	detail, err := e.cinemetaByIMDB(ctx, kind, picked.ID)
	if err == nil {
		return detail, nil
	}
	return infoFromCinemeta(picked, picked.ID), nil
}

func pickCinemeta(metas []cinemetaMeta, title string, year int) cinemetaMeta {
	want := strings.ToLower(strings.TrimSpace(title))
	var best cinemetaMeta
	bestScore := 0
	for _, m := range metas {
		if !strings.HasPrefix(m.ID, "tt") {
			continue
		}
		score := nameScore(want, strings.ToLower(m.Name), year, yearFromRelease(m.ReleaseInfo))
		if score > bestScore {
			bestScore = score
			best = m
		}
	}
	if bestScore < 2 {
		return cinemetaMeta{}
	}
	return best
}

func nameScore(want, got string, wantYear, gotYear int) int {
	if want == "" || got == "" {
		return 0
	}
	score := 0
	if want == got {
		score += 5
	} else if strings.Contains(got, want) || strings.Contains(want, got) {
		score += 3
	} else {
		overlap := 0
		for _, tok := range strings.Fields(want) {
			if len(tok) < 4 {
				continue
			}
			if strings.Contains(got, tok) {
				overlap++
			}
		}
		if overlap == 0 {
			return 0
		}
		score += overlap
	}
	if wantYear > 0 && gotYear > 0 {
		if wantYear == gotYear {
			score += 2
		} else if abs(wantYear-gotYear) <= 1 {
			score++
		} else {
			score--
		}
	}
	return score
}

func abs(n int) int {
	if n < 0 {
		return -n
	}
	return n
}

func infoFromCinemeta(m cinemetaMeta, imdb string) Info {
	if imdb == "" {
		imdb = m.ID
	}
	info := Info{
		ImdbID:      imdb,
		Plot:        strings.TrimSpace(m.Description),
		Genres:      m.Genres,
		Year:        yearFromRelease(m.ReleaseInfo),
		PosterURL:   strings.TrimSpace(m.Poster),
		BackdropURL: strings.TrimSpace(m.Background),
		Source:      "cinemeta",
	}
	if r, err := strconv.ParseFloat(m.IMDBRating, 64); err == nil {
		info.Rating = r
	}
	if info.PosterURL == "" && imdb != "" {
		info.PosterURL = metahubPoster(imdb)
	}
	if info.BackdropURL == "" && imdb != "" {
		info.BackdropURL = metahubBackdrop(imdb)
	}
	return info
}

func cinemetaType(kind string) string {
	if kind == "episode" {
		return "series"
	}
	return "movie"
}

func yearFromRelease(s string) int {
	s = strings.TrimSpace(s)
	if len(s) >= 4 {
		n, _ := strconv.Atoi(s[:4])
		return n
	}
	return 0
}

func metahubPoster(imdb string) string {
	return "https://images.metahub.space/poster/medium/" + imdb + "/img"
}

func metahubBackdrop(imdb string) string {
	return "https://images.metahub.space/background/medium/" + imdb + "/img"
}
