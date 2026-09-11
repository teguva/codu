package taste

import (
	"encoding/json"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

const MinTitles = 8

// Config holds the household match tuning knobs the admin panel can adjust.
type Config struct {
	Enabled        bool    `json:"enabled"`
	PersonalWeight float64 `json:"personalWeight"` // outer blend: personal vs crowd (0..1)
	MinTitles      int     `json:"minTitles"`      // titles needed before personalizing
}

func DefaultConfig() Config {
	return Config{Enabled: true, PersonalWeight: 0.62, MinTitles: MinTitles}
}

func (c Config) normalized() Config {
	if c.PersonalWeight < 0 {
		c.PersonalWeight = 0
	}
	if c.PersonalWeight > 1 {
		c.PersonalWeight = 1
	}
	if c.MinTitles < 1 {
		c.MinTitles = MinTitles
	}
	return c
}

func configPath(dataPath string) string {
	return filepath.Join(dataPath, "taste.json")
}

func LoadConfig(dataPath string) Config {
	cfg := DefaultConfig()
	if b, err := os.ReadFile(configPath(dataPath)); err == nil {
		_ = json.Unmarshal(b, &cfg)
	}
	return cfg.normalized()
}

func SaveConfig(dataPath string, cfg Config) error {
	cfg = cfg.normalized()
	if err := os.MkdirAll(dataPath, 0o755); err != nil {
		return err
	}
	b, err := json.MarshalIndent(cfg, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(configPath(dataPath), b, 0o644)
}

// Tag is a genre or country with its share of the profile mass.
type Tag struct {
	Name  string  `json:"name"`
	Share float64 `json:"share"`
}

type Signal struct {
	ImdbID   string
	Kind     string
	Genres   []string
	GenreIDs []int
	Year     int
	Country  string
	Weight   float64
}

type Profile struct {
	Titles      int                `json:"titles"`
	Genres      map[string]float64 `json:"genres"`
	Countries   map[string]float64 `json:"countries"`
	GenreMass   float64            `json:"genreMass"`
	CountryMass float64            `json:"countryMass"`
	MeanYear    float64            `json:"meanYear"`
}

func Fingerprint(mediaN int, mediaUpdated int64, contN int, contUpdated int64) string {
	return fmt.Sprintf("%d:%d:%d:%d", mediaN, mediaUpdated, contN, contUpdated)
}

func Decode(payload string) (Profile, error) {
	var p Profile
	if err := json.Unmarshal([]byte(payload), &p); err != nil {
		return Profile{}, err
	}
	if p.Genres == nil {
		p.Genres = map[string]float64{}
	}
	if p.Countries == nil {
		p.Countries = map[string]float64{}
	}
	return p, nil
}

func (p Profile) Encode() (string, error) {
	b, err := json.Marshal(p)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

func Build(signals []Signal) Profile {
	p := Profile{
		Genres:    map[string]float64{},
		Countries: map[string]float64{},
	}
	seen := map[string]bool{}
	var yearSum, yearW float64
	for _, sig := range signals {
		w := sig.Weight
		if w <= 0 {
			w = 1
		}
		key := strings.ToLower(strings.TrimSpace(sig.ImdbID))
		if key == "" {
			key = strings.ToLower(sig.Kind + ":" + strings.Join(sig.Genres, ","))
		}
		if key == "" || seen[key] {
			continue
		}
		seen[key] = true
		p.Titles++
		for _, g := range itemGenres(sig.Genres, sig.GenreIDs) {
			p.Genres[g] += w
			p.GenreMass += w
		}
		if c := canonCountry(sig.Country); c != "" {
			p.Countries[c] += w
			p.CountryMass += w
		}
		if sig.Year >= 1920 && sig.Year <= 2100 {
			yearSum += float64(sig.Year) * w
			yearW += w
		}
	}
	if yearW > 0 {
		p.MeanYear = yearSum / yearW
	}
	return p
}

// Score blends the household taste with the public rating. When personalization
// is disabled or the profile is still cold, it returns the crowd score so cards
// fall back to the public number.
func (p Profile) Score(cfg Config, genres []string, genreIDs []int, year int, country string, rating float64) int {
	cfg = cfg.normalized()
	crowd := crowdScore(rating)
	if !cfg.Enabled || p.Titles < cfg.MinTitles {
		return pct(crowd)
	}
	genre := p.genrePart(itemGenres(genres, genreIDs))
	yearPart := p.yearPart(year)
	countryPart := p.countryPart(country)
	personal := 0.72*genre + 0.16*yearPart + 0.12*countryPart
	return pct(cfg.PersonalWeight*personal + (1-cfg.PersonalWeight)*crowd)
}

// ColdStart reports whether the profile still lacks enough titles to personalize.
func (p Profile) ColdStart(cfg Config) bool {
	return !cfg.Enabled || p.Titles < cfg.normalized().MinTitles
}

// TopGenres returns the most-watched genres as shares of the genre mass.
func (p Profile) TopGenres(n int) []Tag {
	return topTags(p.Genres, p.GenreMass, n)
}

// TopCountries returns the most-watched countries as shares of the country mass.
func (p Profile) TopCountries(n int) []Tag {
	return topTags(p.Countries, p.CountryMass, n)
}

func topTags(weights map[string]float64, mass float64, n int) []Tag {
	if len(weights) == 0 {
		return []Tag{}
	}
	tags := make([]Tag, 0, len(weights))
	for name, w := range weights {
		share := 0.0
		if mass > 0 {
			share = w / mass
		}
		tags = append(tags, Tag{Name: name, Share: share})
	}
	sort.Slice(tags, func(i, j int) bool {
		if tags[i].Share != tags[j].Share {
			return tags[i].Share > tags[j].Share
		}
		return tags[i].Name < tags[j].Name
	})
	if n > 0 && len(tags) > n {
		tags = tags[:n]
	}
	return tags
}

func crowdScore(rating float64) float64 {
	if rating <= 0 {
		return 0
	}
	if rating > 10 {
		return 1
	}
	return rating / 10
}

func (p Profile) genrePart(genres []string) float64 {
	if len(genres) == 0 || p.GenreMass <= 0 {
		return 0.45
	}
	var hit float64
	for _, g := range genres {
		hit += p.Genres[g]
	}
	return math.Min(1, hit/p.GenreMass)
}

func (p Profile) yearPart(year int) float64 {
	if year <= 0 || p.MeanYear <= 0 {
		return 0.5
	}
	delta := math.Abs(float64(year) - p.MeanYear)
	switch {
	case delta <= 7:
		return 1
	case delta >= 28:
		return 0
	default:
		return 1 - (delta-7)/21
	}
}

func (p Profile) countryPart(country string) float64 {
	if p.CountryMass <= 0 {
		return 0.5
	}
	c := canonCountry(country)
	if c == "" {
		return 0.5
	}
	share := p.Countries[c] / p.CountryMass
	return math.Min(1, share*3)
}

func pct(v float64) int {
	if v <= 0 {
		return 0
	}
	n := int(math.Round(v * 100))
	if n < 1 {
		return 1
	}
	if n > 99 {
		return 99
	}
	return n
}
