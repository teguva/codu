package taste

import "strings"

// TMDB genre ids used on list payloads that omit genre names.
var tmdbGenreName = map[int]string{
	12:    "Adventure",
	14:    "Fantasy",
	16:    "Animation",
	18:    "Drama",
	27:    "Horror",
	28:    "Action",
	35:    "Comedy",
	36:    "History",
	37:    "Western",
	53:    "Thriller",
	80:    "Crime",
	99:    "Documentary",
	878:   "Science Fiction",
	9648:  "Mystery",
	10402: "Music",
	10749: "Romance",
	10751: "Family",
	10752: "War",
	10759: "Action",
	10762: "Family",
	10763: "Documentary",
	10764: "Reality",
	10765: "Science Fiction",
	10766: "Drama",
	10767: "Talk",
	10768: "War",
	10770: "TV Movie",
}

var genreAlias = map[string]string{
	"sci-fi":             "science fiction",
	"sci fi":             "science fiction",
	"scifi":              "science fiction",
	"science-fiction":    "science fiction",
	"sci-fi & fantasy":   "science fiction",
	"sci-fi and fantasy": "science fiction",
	"action & adventure": "action",
	"war & politics":     "war",
	"tv movie":           "drama",
}

func canonGenre(raw string) []string {
	raw = strings.ToLower(strings.TrimSpace(raw))
	if raw == "" {
		return nil
	}
	if alias, ok := genreAlias[raw]; ok {
		raw = alias
	}
	parts := strings.FieldsFunc(raw, func(r rune) bool {
		return r == '&' || r == '/' || r == ','
	})
	out := make([]string, 0, len(parts)+1)
	seen := map[string]bool{}
	add := func(s string) {
		s = strings.ToLower(strings.TrimSpace(s))
		if s == "" || seen[s] {
			return
		}
		if alias, ok := genreAlias[s]; ok {
			s = alias
		}
		if seen[s] {
			return
		}
		seen[s] = true
		out = append(out, s)
	}
	if len(parts) > 1 {
		for _, p := range parts {
			add(p)
		}
	} else {
		add(raw)
	}
	return out
}

func itemGenres(names []string, ids []int) []string {
	seen := map[string]bool{}
	out := make([]string, 0, len(names)+len(ids))
	add := func(list []string) {
		for _, g := range list {
			if seen[g] {
				continue
			}
			seen[g] = true
			out = append(out, g)
		}
	}
	for _, id := range ids {
		if name, ok := tmdbGenreName[id]; ok {
			add(canonGenre(name))
		}
	}
	for _, name := range names {
		add(canonGenre(name))
	}
	return out
}

func canonCountry(raw string) string {
	raw = strings.ToUpper(strings.TrimSpace(raw))
	switch raw {
	case "UNITED STATES", "UNITED STATES OF AMERICA", "USA", "US":
		return "USA"
	case "UNITED KINGDOM", "GREAT BRITAIN", "GB", "UK":
		return "UK"
	default:
		return raw
	}
}
