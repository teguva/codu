package api

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"

	"coog/internal/meta"
	"coog/internal/settings"
	"coog/internal/store"
	"coog/internal/streams"
)

func (s *Server) handleCatalogHome(w http.ResponseWriter, r *http.Request) {
	movies, series, err := s.meta.HomeCatalog(r.Context())
	if err != nil {
		s.setCatalogError(err.Error())
		s.note("error", "api", "catalog.error", err.Error(), "", "")
		writeError(w, http.StatusBadGateway, err.Error())
		return
	}
	s.setCatalogError("")
	localMovies, localSeries := s.imdbIndex()
	writeJSON(w, http.StatusOK, map[string]any{
		"trendingMovies": attachLibrary(movies, localMovies),
		"trendingSeries": attachLibrary(series, localSeries),
	})
}

func (s *Server) handleCatalogShow(w http.ResponseWriter, r *http.Request) {
	imdb := strings.TrimSpace(r.PathValue("imdb"))
	if !strings.HasPrefix(imdb, "tt") {
		writeError(w, http.StatusBadRequest, "imdb id required")
		return
	}
	cover, eps, err := s.meta.CatalogShow(r.Context(), imdb)
	if err != nil {
		writeError(w, http.StatusBadGateway, err.Error())
		return
	}
	if detail, err := s.meta.CatalogTitle(r.Context(), "series", imdb); err == nil {
		if len(detail.Cast) > 0 {
			cover.Cast = detail.Cast
		}
		if detail.Plot != "" {
			cover.Plot = detail.Plot
		}
		if detail.Title != "" {
			cover.Title = detail.Title
		}
	}
	_, localSeries := s.imdbIndex()
	covers := attachLibrary([]meta.CatalogItem{cover}, localSeries)
	cover = covers[0]
	eps = s.attachEpisodeLibrary(eps, imdb)
	writeJSON(w, http.StatusOK, map[string]any{
		"item":     catalogAsMedia(cover),
		"episodes": catalogListAsMedia(eps),
	})
}

func (s *Server) imdbIndex() (movies, series map[string]string) {
	movies, series = map[string]string{}, map[string]string{}
	items, err := s.store.ListMedia()
	if err != nil {
		return movies, series
	}
	for _, item := range items {
		info, ok := s.meta.Peek(item.ID)
		if !ok || !meta.IdentityConfirmed(item.Path, info) {
			continue
		}
		id := strings.ToLower(info.ImdbID)
		switch item.Kind {
		case "movie":
			if _, exists := movies[id]; !exists {
				movies[id] = item.ID
			}
		case "episode":
			if _, exists := series[id]; !exists {
				series[id] = item.ID
			}
		}
	}
	return movies, series
}

func attachLibrary(items []meta.CatalogItem, local map[string]string) []meta.CatalogItem {
	out := make([]meta.CatalogItem, 0, len(items))
	for _, item := range items {
		if mediaID, ok := local[strings.ToLower(item.ImdbID)]; ok {
			item.MediaID = mediaID
			item.InLibrary = true
		}
		out = append(out, item)
	}
	return out
}

func catalogAsMedia(item meta.CatalogItem) map[string]any {
	out := map[string]any{
		"id":             item.ID,
		"kind":           item.Kind,
		"title":          item.Title,
		"year":           item.Year,
		"season":         item.Season,
		"episode":        item.Episode,
		"showTitle":      item.ShowTitle,
		"plot":           item.Plot,
		"posterUrl":      item.PosterURL,
		"backdropUrl":    item.BackdropURL,
		"imdbId":         item.ImdbID,
		"rating":         item.Rating,
		"genres":         item.Genres,
		"path":           "",
		"inLibrary":      item.InLibrary,
		"mediaId":        item.MediaID,
		"releasePhase":   item.ReleasePhase,
		"tmdbId":         item.TMDBID,
		"matchStatus":    "matched",
		"runtimeMinutes": item.RuntimeMinutes,
		"certification":  item.Certification,
		"country":        item.Country,
	}
	if item.Genres == nil {
		out["genres"] = []string{}
	}
	if len(item.Cast) > 0 {
		out["cast"] = item.Cast
	}
	if item.Director != nil {
		out["director"] = item.Director
	}
	return out
}

func catalogListAsMedia(items []meta.CatalogItem) []map[string]any {
	out := make([]map[string]any, 0, len(items))
	for _, item := range items {
		out = append(out, catalogAsMedia(item))
	}
	return out
}

func (s *Server) findLocalByIMDB(imdb, kind string) (store.MediaItem, bool) {
	imdb = strings.ToLower(strings.TrimSpace(imdb))
	if imdb == "" {
		return store.MediaItem{}, false
	}
	items, err := s.store.ListMedia()
	if err != nil {
		return store.MediaItem{}, false
	}
	wantMovie := kind == "" || kind == "movie"
	for _, item := range items {
		info, ok := s.meta.Peek(item.ID)
		if !ok || !meta.IdentityConfirmed(item.Path, info) || strings.ToLower(info.ImdbID) != imdb {
			continue
		}
		if wantMovie && item.Kind == "movie" {
			return item, true
		}
		if !wantMovie && item.Kind == "episode" {
			return item, true
		}
	}
	return store.MediaItem{}, false
}

func (s *Server) attachEpisodeLibrary(eps []meta.CatalogItem, imdb string) []meta.CatalogItem {
	items, err := s.store.ListMedia()
	if err != nil {
		return eps
	}
	return overlayEpisodeLibrary(eps, imdb, items, func(item store.MediaItem) string {
		info, ok := s.meta.Peek(item.ID)
		if !ok || !meta.IdentityConfirmed(item.Path, info) {
			return ""
		}
		return info.ImdbID
	})
}

func overlayEpisodeLibrary(eps []meta.CatalogItem, imdb string, local []store.MediaItem, imdbOf func(store.MediaItem) string) []meta.CatalogItem {
	imdb = strings.ToLower(strings.TrimSpace(imdb))
	type key struct {
		season, episode int
	}
	files := map[key]store.MediaItem{}
	var extras []store.MediaItem
	for _, item := range local {
		if item.Kind != "episode" {
			continue
		}
		if strings.ToLower(strings.TrimSpace(imdbOf(item))) != imdb {
			continue
		}
		k := key{item.Season, item.Episode}
		if _, exists := files[k]; !exists {
			files[k] = item
		}
		extras = append(extras, item)
	}
	seen := map[key]bool{}
	out := make([]meta.CatalogItem, 0, len(eps)+len(extras))
	for _, ep := range eps {
		k := key{ep.Season, ep.Episode}
		seen[k] = true
		if item, ok := files[k]; ok {
			ep.MediaID = item.ID
			ep.InLibrary = true
		}
		out = append(out, ep)
	}
	for _, item := range extras {
		k := key{item.Season, item.Episode}
		if seen[k] {
			continue
		}
		seen[k] = true
		out = append(out, meta.CatalogItem{
			ID:        "library:" + item.ID,
			Kind:      "episode",
			Title:     item.Title,
			ShowTitle: item.ShowTitle,
			Season:    item.Season,
			Episode:   item.Episode,
			Year:      item.Year,
			ImdbID:    imdb,
			MediaID:   item.ID,
			InLibrary: true,
		})
	}
	return out
}

func (s *Server) handleCatalogStreams(w http.ResponseWriter, r *http.Request) {
	imdb := strings.TrimSpace(r.URL.Query().Get("imdb"))
	if !strings.HasPrefix(imdb, "tt") {
		writeError(w, http.StatusBadRequest, "imdb id required")
		return
	}
	kind := strings.TrimSpace(r.URL.Query().Get("kind"))
	season, _ := strconv.Atoi(r.URL.Query().Get("season"))
	episode, _ := strconv.Atoi(r.URL.Query().Get("episode"))
	cfg := settings.Load(s.cfg.DataPath)
	cands, err := streams.SearchTorrentio(r.Context(), cfg, kind, imdb, season, episode)
	if err != nil {
		writeError(w, http.StatusBadGateway, err.Error())
		return
	}
	cands = streams.CapCandidates(cands, 40)
	out := make([]map[string]any, 0, len(cands))
	for _, c := range cands {
		out = append(out, streams.PublicCandidate(c))
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": out})
}

func (s *Server) handleCatalogSearch(w http.ResponseWriter, r *http.Request) {
	q := strings.TrimSpace(r.URL.Query().Get("q"))
	if q == "" {
		writeError(w, http.StatusBadRequest, "q is required")
		return
	}
	result, err := s.meta.Search(r.Context(), q)
	if err != nil {
		if !s.meta.TMDBEnabled() {
			writeJSON(w, http.StatusOK, map[string]any{
				"movies": []any{},
				"series": []any{},
				"people": []any{},
				"error":  err.Error(),
			})
			return
		}
		writeError(w, http.StatusBadGateway, err.Error())
		return
	}
	localMovies, localSeries := s.imdbIndex()
	writeJSON(w, http.StatusOK, map[string]any{
		"movies": catalogListAsMedia(attachLibrary(result.Movies, localMovies)),
		"series": catalogListAsMedia(attachLibrary(result.Series, localSeries)),
		"people": result.People,
	})
}

func (s *Server) handleCatalogTitle(w http.ResponseWriter, r *http.Request) {
	imdb := strings.TrimSpace(r.PathValue("imdb"))
	kind := strings.TrimSpace(r.URL.Query().Get("kind"))
	if kind == "" {
		kind = "movie"
	}
	item, err := s.meta.CatalogTitle(r.Context(), kind, imdb)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	localMovies, localSeries := s.imdbIndex()
	local := localMovies
	if kind == "series" || kind == "episode" {
		local = localSeries
	}
	item = attachLibrary([]meta.CatalogItem{item}, local)[0]
	writeJSON(w, http.StatusOK, catalogAsMedia(item))
}

func (s *Server) handleCatalogSimilar(w http.ResponseWriter, r *http.Request) {
	imdb := strings.TrimSpace(r.PathValue("imdb"))
	kind := strings.TrimSpace(r.URL.Query().Get("kind"))
	if kind == "" {
		kind = "movie"
	}
	items, err := s.meta.Similar(r.Context(), kind, imdb)
	if err != nil {
		if !s.meta.TMDBEnabled() {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		writeError(w, http.StatusBadGateway, err.Error())
		return
	}
	localMovies, localSeries := s.imdbIndex()
	local := localMovies
	if kind == "series" || kind == "episode" {
		local = localSeries
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"items": catalogListAsMedia(attachLibrary(items, local)),
	})
}

func (s *Server) handleCatalogTMDB(w http.ResponseWriter, r *http.Request) {
	kind := strings.TrimSpace(r.PathValue("kind"))
	id, _ := strconv.Atoi(r.PathValue("id"))
	if id == 0 {
		writeError(w, http.StatusBadRequest, "tmdb id required")
		return
	}
	item, err := s.meta.CatalogByTMDB(r.Context(), kind, id)
	if err != nil {
		writeError(w, http.StatusBadGateway, err.Error())
		return
	}
	localMovies, localSeries := s.imdbIndex()
	local := localMovies
	if item.Kind == "series" || kind == "tv" || kind == "series" {
		local = localSeries
	}
	item = attachLibrary([]meta.CatalogItem{item}, local)[0]
	writeJSON(w, http.StatusOK, catalogAsMedia(item))
}

func (s *Server) handleCatalogPerson(w http.ResponseWriter, r *http.Request) {
	id, _ := strconv.Atoi(r.PathValue("id"))
	person, err := s.meta.PersonCredits(r.Context(), id)
	if err != nil {
		if !s.meta.TMDBEnabled() {
			writeError(w, http.StatusBadRequest, err.Error())
			return
		}
		writeError(w, http.StatusBadGateway, err.Error())
		return
	}
	localMovies, localSeries := s.imdbIndex()
	credits := make([]map[string]any, 0, len(person.Credits))
	for _, c := range person.Credits {
		local := localMovies
		if c.Kind == "series" {
			local = localSeries
		}
		c = attachLibrary([]meta.CatalogItem{c}, local)[0]
		credits = append(credits, catalogAsMedia(c))
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"tmdbId":             person.TMDBID,
		"name":               person.Name,
		"profileUrl":         person.ProfileURL,
		"knownForDepartment": person.KnownForDepartment,
		"biography":          person.Biography,
		"birthday":           person.Birthday,
		"placeOfBirth":       person.PlaceOfBirth,
		"credits":            credits,
	})
}

func (s *Server) handleStreamingSettings(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		s.writeStreamingSettings(w)
	case http.MethodPut, http.MethodPost:
		var body map[string]any
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			writeError(w, http.StatusBadRequest, "invalid json")
			return
		}
		cfg := settings.Load(s.cfg.DataPath)
		if v, ok := body["saveToLibrary"].(bool); ok {
			cfg.SaveToLibrary = v
		}
		if v, ok := body["autoplayNextEpisode"].(bool); ok {
			cfg.AutoplayNextEpisode = v
		}
		if v, ok := body["autoDownloadNextEpisode"].(bool); ok {
			cfg.AutoDownloadNext = v
		}
		if v, ok := body["includeWebStreams"].(bool); ok {
			cfg.IncludeWebStreams = v
		}
		if v, ok := body["prefetchBeforeEndMinutes"].(float64); ok {
			cfg.PrefetchMinutes = int(v)
		}
		if v, ok := body["prefetchCount"].(float64); ok {
			cfg.PrefetchCount = int(v)
		}
		if v, ok := body["continueOverlaySeconds"].(float64); ok {
			cfg.ContinueOverlaySec = int(v)
		}
		if v, ok := body["realDebridToken"].(string); ok {
			cfg.RealDebridToken = strings.TrimSpace(v)
		}
		if v, ok := body["torrentioProviders"]; ok {
			if list := asStringSlice(v); list != nil {
				cfg.TorrentioProviders = list
			}
		}
		if v, ok := body["excludeQualities"]; ok {
			if list := asStringSlice(v); list != nil {
				cfg.ExcludeQualities = list
			}
		}
		if err := settings.Save(s.cfg.DataPath, cfg); err != nil {
			writeError(w, http.StatusInternalServerError, err.Error())
			return
		}
		s.rdMu.Lock()
		s.rdStatus = nil
		s.rdMu.Unlock()
		s.writeStreamingSettings(w)
	default:
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (s *Server) writeStreamingSettings(w http.ResponseWriter) {
	cfg := settings.Load(s.cfg.DataPath)
	writeJSON(w, http.StatusOK, map[string]any{
		"saveToLibrary":            cfg.SaveToLibrary,
		"autoplayNextEpisode":      cfg.AutoplayNextEpisode,
		"autoDownloadNextEpisode":  cfg.AutoDownloadNext,
		"prefetchBeforeEndMinutes": cfg.PrefetchMinutes,
		"prefetchCount":            cfg.PrefetchCount,
		"continueOverlaySeconds":   cfg.ContinueOverlaySec,
		"includeWebStreams":        cfg.IncludeWebStreams,
		"torrentioProviders":       cfg.TorrentioProviders,
		"excludeQualities":         cfg.ExcludeQualities,
		"realDebridConfigured":     strings.TrimSpace(cfg.RealDebridToken) != "",
		"realDebridTokenMasked":    settings.MaskToken(cfg.RealDebridToken),
	})
}

func asStringSlice(v any) []string {
	switch t := v.(type) {
	case []string:
		return cleanStrings(t)
	case []any:
		out := make([]string, 0, len(t))
		for _, item := range t {
			s, ok := item.(string)
			if !ok {
				continue
			}
			s = strings.TrimSpace(s)
			if s != "" {
				out = append(out, s)
			}
		}
		return out
	case string:
		parts := strings.Split(t, ",")
		return cleanStrings(parts)
	default:
		return nil
	}
}

func cleanStrings(in []string) []string {
	out := make([]string, 0, len(in))
	seen := map[string]bool{}
	for _, s := range in {
		s = strings.TrimSpace(s)
		if s == "" || seen[s] {
			continue
		}
		seen[s] = true
		out = append(out, s)
	}
	return out
}
