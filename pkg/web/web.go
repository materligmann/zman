// Package web sert le site public (horloge, manifeste, documentation de
// l'API, méthode) à partir de fichiers embarqués dans le binaire, en trois
// langues, et monte l'API sous /api/.
package web

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"html/template"
	"io/fs"
	"net/http"
	"path"
	"sort"
	"strings"
	"sync"
	"time"

	"zman/pkg/api"
)

// Langs sont les langues servies, dans l'ordre du sélecteur.
var Langs = []string{"fr", "en", "he"}

// Pages sont les slugs des pages, identiques dans toutes les langues.
var Pages = []string{"", "manifeste", "api", "methode"}

const cookieName = "lang"

// Config du site.
type Config struct {
	BaseURL string // ex. https://zman.example ; vide = déduit de la requête
	Repo    string // URL du dépôt
	Version string // identifiant de build, pour le versionnage des assets
}

// Strings sont les chaînes d'interface d'une langue (web/i18n/<lang>.json).
type Strings struct {
	Name      string            `json:"name"`
	Dir       string            `json:"dir"`
	Titles    map[string]string `json:"titles"`
	Descs     map[string]string `json:"descriptions"`
	Nav       map[string]string `json:"nav"`
	Footer    map[string]string `json:"footer"`
	Weekdays  []string          `json:"weekdays"`
	Months    []string          `json:"months"`
	AdarI     string            `json:"adar_i"`
	AdarII    string            `json:"adar_ii"`
	Clock     map[string]string `json:"clock"`
	LangNames map[string]string `json:"lang_names"`
	OGLocale  string            `json:"og_locale"`
}

// Site est le handler du site.
type Site struct {
	cfg      Config
	fsys     fs.FS
	static   fs.FS
	strings  map[string]*Strings
	layout   *template.Template
	content  map[string]*template.Template // "<lang>/<page>"
	examples map[string]string
	assetVer string
	api      http.Handler
	mux      *http.ServeMux
	once     sync.Once
}

// New construit le site à partir de fsys (racine web/) et du handler de
// l'API, monté sous /api/.
func New(fsys fs.FS, apiHandler http.Handler, cfg Config) (*Site, error) {
	s := &Site{cfg: cfg, fsys: fsys, api: apiHandler, strings: map[string]*Strings{}, content: map[string]*template.Template{}, examples: map[string]string{}}
	var err error
	if s.static, err = fs.Sub(fsys, "static"); err != nil {
		return nil, err
	}
	for _, l := range Langs {
		b, err := fs.ReadFile(fsys, "i18n/"+l+".json")
		if err != nil {
			return nil, err
		}
		var st Strings
		if err := json.Unmarshal(b, &st); err != nil {
			return nil, fmt.Errorf("i18n/%s.json: %w", l, err)
		}
		s.strings[l] = &st
	}
	funcs := template.FuncMap{
		"asset": s.asset,
		"safe":  func(v string) template.HTML { return template.HTML(v) },
	}
	s.layout, err = template.New("layout.html").Funcs(funcs).ParseFS(fsys, "templates/layout.html")
	if err != nil {
		return nil, err
	}
	for _, l := range Langs {
		for _, p := range Pages {
			name := pageFile(p)
			t, err := template.New(name).Funcs(funcs).ParseFS(fsys, "content/"+l+"/"+name)
			if err != nil {
				return nil, err
			}
			s.content[l+"/"+p] = t
		}
	}
	if entries, err := fs.ReadDir(fsys, "examples"); err == nil {
		for _, e := range entries {
			b, err := fs.ReadFile(fsys, "examples/"+e.Name())
			if err == nil {
				s.examples[strings.TrimSuffix(e.Name(), path.Ext(e.Name()))] = strings.TrimRight(string(b), "\n")
			}
		}
	}
	s.assetVer = s.computeAssetVersion()
	s.routes()
	return s, nil
}

func pageFile(p string) string {
	if p == "" {
		return "index.html"
	}
	return p + ".html"
}

func (s *Site) computeAssetVersion() string {
	if s.cfg.Version != "" {
		return s.cfg.Version
	}
	h := sha256.New()
	_ = fs.WalkDir(s.static, ".", func(p string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		b, _ := fs.ReadFile(s.static, p)
		h.Write([]byte(p))
		h.Write(b)
		return nil
	})
	return hex.EncodeToString(h.Sum(nil))[:12]
}

func (s *Site) asset(p string) string {
	return "/static/" + strings.TrimPrefix(p, "/") + "?v=" + s.assetVer
}

// ServeHTTP implémente http.Handler.
func (s *Site) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	s.mux.ServeHTTP(w, r)
}

func (s *Site) routes() {
	mux := http.NewServeMux()
	s.mux = mux
	// API et redirections des anciens chemins.
	mux.Handle("/api/", s.api)
	api.RegisterLegacyRedirects(mux, "/api")
	// Documentation de l'API : /api (via la boucle des pages) et /api/
	// (sauf si le client veut du JSON : index de l'API).
	mux.HandleFunc("GET /api/{$}", func(w http.ResponseWriter, r *http.Request) {
		if wantsJSON(r) {
			s.api.ServeHTTP(w, r)
			return
		}
		s.page(w, r, "", "api")
	})
	// Pages, avec ou sans préfixe de langue. Les préfixes sont explicites
	// (pas de joker) pour ne pas entrer en conflit avec /api/.
	for _, p := range Pages {
		p := p
		mux.HandleFunc("GET /"+p+pathEnd(p), func(w http.ResponseWriter, r *http.Request) { s.page(w, r, "", p) })
		for _, l := range Langs {
			l := l
			mux.HandleFunc("GET /"+l+"/"+p+pathEnd(p), func(w http.ResponseWriter, r *http.Request) { s.page(w, r, l, p) })
		}
	}
	mux.HandleFunc("GET /static/", s.serveStatic)
	mux.HandleFunc("GET /robots.txt", s.robots)
	mux.HandleFunc("GET /sitemap.xml", s.sitemap)
	mux.HandleFunc("GET /favicon.ico", func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, s.asset("favicon.svg"), http.StatusFound)
	})
}

// pathEnd renvoie "{$}" pour la racine (motif exact) et "" sinon.
func pathEnd(p string) string {
	if p == "" {
		return "{$}"
	}
	return ""
}

func wantsJSON(r *http.Request) bool {
	a := r.Header.Get("Accept")
	return strings.Contains(a, "application/json") && !strings.Contains(a, "text/html")
}

// pageData est le contexte passé aux templates.
type pageData struct {
	Lang       string
	Dir        string
	Page       string
	Path       string
	S          *Strings
	Title      string
	Desc       string
	Canonical  string
	Base       string
	Alternates []alternate
	OGImage    string
	Repo       string
	Examples   map[string]string
	Langs      []langLink
	Body       template.HTML
	I18N       template.JS
}

type alternate struct{ Lang, URL string }
type langLink struct {
	Code, Name, URL string
	Current         bool
}

// page rend une page dans la langue explicite ou déduite.
func (s *Site) page(w http.ResponseWriter, r *http.Request, lang, p string) {
	// ?lang=xx : mémorise la préférence et revient sur la page nue.
	if q := r.URL.Query().Get("lang"); q != "" {
		if s.strings[q] != nil {
			http.SetCookie(w, &http.Cookie{Name: cookieName, Value: q, Path: "/", MaxAge: 365 * 24 * 3600, SameSite: http.SameSiteLaxMode, Secure: isHTTPS(r)})
		}
		http.Redirect(w, r, "/"+p, http.StatusSeeOther)
		return
	}
	if lang == "" {
		lang = s.detectLang(r)
	}
	st := s.strings[lang]
	pagePath := "/" + p
	base := s.baseURL(r)
	d := pageData{
		Lang: lang, Dir: st.Dir, Page: p, Path: pagePath, S: st,
		Title:     st.Titles[pageKey(p)],
		Desc:      st.Descs[pageKey(p)],
		Canonical: base + "/" + lang + pagePath,
		Base:      base,
		OGImage:   base + "/static/og.png",
		Repo:      s.cfg.Repo,
		Examples:  s.examples,
	}
	for _, l := range Langs {
		d.Alternates = append(d.Alternates, alternate{l, base + "/" + l + pagePath})
		d.Langs = append(d.Langs, langLink{Code: l, Name: st.LangNames[l], URL: pagePath + "?lang=" + l, Current: l == lang})
	}
	if p == "" {
		i18n, _ := json.Marshal(map[string]any{
			"weekdays": st.Weekdays, "months": st.Months, "adar_i": st.AdarI, "adar_ii": st.AdarII, "clock": st.Clock, "lang": lang,
		})
		d.I18N = template.JS(i18n)
	}
	var body bytes.Buffer
	if err := s.content[lang+"/"+p].Execute(&body, d); err != nil {
		http.Error(w, "template error: "+err.Error(), http.StatusInternalServerError)
		return
	}
	d.Body = template.HTML(body.String())
	var out bytes.Buffer
	if err := s.layout.Execute(&out, d); err != nil {
		http.Error(w, "template error: "+err.Error(), http.StatusInternalServerError)
		return
	}
	h := w.Header()
	h.Set("Content-Type", "text/html; charset=utf-8")
	h.Set("Content-Language", lang)
	h.Set("Vary", "Cookie, Accept-Language")
	h.Set("Cache-Control", "private, max-age=60")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write(out.Bytes())
}

func pageKey(p string) string {
	if p == "" {
		return "index"
	}
	return p
}

func (s *Site) detectLang(r *http.Request) string {
	if c, err := r.Cookie(cookieName); err == nil && s.strings[c.Value] != nil {
		return c.Value
	}
	for _, part := range strings.Split(r.Header.Get("Accept-Language"), ",") {
		tag := strings.ToLower(strings.TrimSpace(strings.SplitN(part, ";", 2)[0]))
		if i := strings.Index(tag, "-"); i > 0 {
			tag = tag[:i]
		}
		if tag == "iw" {
			tag = "he"
		}
		if s.strings[tag] != nil {
			return tag
		}
	}
	return Langs[0]
}

func isHTTPS(r *http.Request) bool {
	return r.TLS != nil || strings.EqualFold(r.Header.Get("X-Forwarded-Proto"), "https")
}

func (s *Site) baseURL(r *http.Request) string {
	if s.cfg.BaseURL != "" {
		return strings.TrimSuffix(s.cfg.BaseURL, "/")
	}
	scheme := "http"
	if isHTTPS(r) {
		scheme = "https"
	}
	host := r.Host
	if fh := r.Header.Get("X-Forwarded-Host"); fh != "" {
		host = fh
	}
	return scheme + "://" + host
}

func (s *Site) serveStatic(w http.ResponseWriter, r *http.Request) {
	name := strings.TrimPrefix(r.URL.Path, "/static/")
	f, err := s.static.Open(name)
	if err != nil {
		http.NotFound(w, r)
		return
	}
	defer f.Close()
	st, err := f.Stat()
	if err != nil || st.IsDir() {
		http.NotFound(w, r)
		return
	}
	if r.URL.Query().Get("v") != "" {
		w.Header().Set("Cache-Control", "public, max-age=31536000, immutable")
	} else {
		w.Header().Set("Cache-Control", "public, max-age=3600")
	}
	if strings.HasSuffix(name, ".woff2") {
		w.Header().Set("Content-Type", "font/woff2")
	}
	rs, ok := f.(interface {
		Seek(int64, int) (int64, error)
		Read([]byte) (int, error)
	})
	if !ok {
		http.NotFound(w, r)
		return
	}
	http.ServeContent(w, r, name, time.Time{}, rs)
}

func (s *Site) robots(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.Header().Set("Cache-Control", "public, max-age=86400")
	fmt.Fprintf(w, "User-agent: *\nAllow: /\nDisallow: /api/\nSitemap: %s/sitemap.xml\n", s.baseURL(r))
}

func (s *Site) sitemap(w http.ResponseWriter, r *http.Request) {
	base := s.baseURL(r)
	w.Header().Set("Content-Type", "application/xml; charset=utf-8")
	w.Header().Set("Cache-Control", "public, max-age=86400")
	var b strings.Builder
	b.WriteString(`<?xml version="1.0" encoding="UTF-8"?>` + "\n")
	b.WriteString(`<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">` + "\n")
	pages := append([]string(nil), Pages...)
	sort.Strings(pages)
	for _, p := range pages {
		for _, l := range Langs {
			fmt.Fprintf(&b, "  <url>\n    <loc>%s/%s/%s</loc>\n", base, l, p)
			for _, alt := range Langs {
				fmt.Fprintf(&b, "    <xhtml:link rel=\"alternate\" hreflang=\"%s\" href=\"%s/%s/%s\"/>\n", alt, base, alt, p)
			}
			fmt.Fprintf(&b, "    <xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"%s/%s\"/>\n  </url>\n", base, p)
		}
	}
	b.WriteString("</urlset>\n")
	_, _ = w.Write([]byte(b.String()))
}

// SecurityHeaders ajoute des en-têtes de sécurité raisonnables à toutes
// les réponses. Le site n'a ni script ni style externes.
func SecurityHeaders(next http.Handler) http.Handler {
	const csp = "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'"
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		h := w.Header()
		h.Set("Content-Security-Policy", csp)
		h.Set("X-Content-Type-Options", "nosniff")
		h.Set("Referrer-Policy", "strict-origin-when-cross-origin")
		h.Set("X-Frame-Options", "DENY")
		h.Set("Permissions-Policy", "camera=(), microphone=(), geolocation=(), interest-cohort=()")
		h.Set("Cross-Origin-Opener-Policy", "same-origin")
		if isHTTPS(r) {
			h.Set("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
		}
		next.ServeHTTP(w, r)
	})
}
