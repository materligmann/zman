package web

import (
	"io"
	"net/http"
	"net/http/httptest"
	"regexp"
	"strings"
	"testing"

	"zman/pkg/api"
	"zman/pkg/clock"
	"zman/pkg/luach"
	"zman/pkg/rega"
	webfs "zman/web"
)

func newSite(t *testing.T) *httptest.Server {
	t.Helper()
	d, _ := luach.DayFromDate(5786, luach.Elul, 25)
	c := clock.Fixed{R: rega.FromParts(d, 13, 782, 6), Q: clock.Quality{Source: "iers", DUT1AgeDays: 4.5, Predicted: true}}
	a := api.New(c, api.WithPrefix("/api"))
	s, err := New(webfs.FS, a.Handler(), Config{Repo: "https://example.invalid/repo"})
	if err != nil {
		t.Fatal(err)
	}
	srv := httptest.NewServer(SecurityHeaders(s))
	t.Cleanup(srv.Close)
	return srv
}

var noRedirect = &http.Client{CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}

func get(t *testing.T, srv *httptest.Server, path string, hdr map[string]string) (*http.Response, string) {
	t.Helper()
	req, _ := http.NewRequest(http.MethodGet, srv.URL+path, nil)
	for k, v := range hdr {
		req.Header.Set(k, v)
	}
	resp, err := noRedirect.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	b, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	return resp, string(b)
}

func TestPagesAllLanguages(t *testing.T) {
	srv := newSite(t)
	for _, l := range Langs {
		for _, p := range Pages {
			resp, body := get(t, srv, "/"+l+"/"+p, nil)
			if resp.StatusCode != 200 {
				t.Errorf("/%s/%s: %d", l, p, resp.StatusCode)
				continue
			}
			if !strings.Contains(body, `<html lang="`+l+`"`) {
				t.Errorf("/%s/%s: missing lang attribute", l, p)
			}
			wantDir := "ltr"
			if l == "he" {
				wantDir = "rtl"
			}
			if !strings.Contains(body, `dir="`+wantDir+`"`) {
				t.Errorf("/%s/%s: dir should be %s", l, p, wantDir)
			}
			if resp.Header.Get("Content-Language") != l {
				t.Errorf("/%s/%s: Content-Language %q", l, p, resp.Header.Get("Content-Language"))
			}
			if !strings.Contains(body, `hreflang="he"`) || !strings.Contains(body, `rel="canonical"`) || !strings.Contains(body, `og:image`) {
				t.Errorf("/%s/%s: missing SEO tags", l, p)
			}
		}
	}
}

func TestLanguageSelection(t *testing.T) {
	srv := newSite(t)
	// Défaut : français.
	if _, body := get(t, srv, "/manifeste", nil); !strings.Contains(body, `<html lang="fr"`) {
		t.Error("default should be fr")
	}
	// Accept-Language.
	if _, body := get(t, srv, "/manifeste", map[string]string{"Accept-Language": "he-IL,he;q=0.9,en;q=0.8"}); !strings.Contains(body, `<html lang="he"`) {
		t.Error("Accept-Language he ignored")
	}
	if _, body := get(t, srv, "/", map[string]string{"Accept-Language": "de, en-GB;q=0.8"}); !strings.Contains(body, `<html lang="en"`) {
		t.Error("Accept-Language en ignored")
	}
	// ?lang= pose le cookie et redirige.
	resp, _ := get(t, srv, "/methode?lang=he", nil)
	if resp.StatusCode != 303 || resp.Header.Get("Location") != "/methode" {
		t.Fatalf("?lang: %d %s", resp.StatusCode, resp.Header.Get("Location"))
	}
	var cookie string
	for _, c := range resp.Cookies() {
		if c.Name == "lang" {
			cookie = c.Value
		}
	}
	if cookie != "he" {
		t.Fatalf("cookie = %q", cookie)
	}
	if _, body := get(t, srv, "/methode", map[string]string{"Cookie": "lang=he", "Accept-Language": "fr"}); !strings.Contains(body, `<html lang="he" dir="rtl"`) {
		t.Error("cookie should win over Accept-Language")
	}
	if resp, _ := get(t, srv, "/xx/manifeste", nil); resp.StatusCode != 404 {
		t.Errorf("unknown lang: %d", resp.StatusCode)
	}
}

func TestClockPageHasNoForeignTime(t *testing.T) {
	srv := newSite(t)
	yearRe := regexp.MustCompile(`\b(19|20)\d\d\b`)
	for _, l := range Langs {
		_, body := get(t, srv, "/"+l+"/", nil)
		lower := strings.ToLower(body)
		for _, w := range []string{"utc", "unix", "janvier", "january", "september", "septembre", "gmt", "iso 8601"} {
			if strings.Contains(lower, w) {
				t.Errorf("/%s/: clock page mentions %q", l, w)
			}
		}
		if m := yearRe.FindString(body); m != "" {
			t.Errorf("/%s/: clock page contains a Gregorian-looking year %q", l, m)
		}
		if !strings.Contains(body, "/api/now") && !strings.Contains(body, "clock.js") {
			t.Errorf("/%s/: clock script missing", l)
		}
	}
}

func TestAPIMountAndDocs(t *testing.T) {
	srv := newSite(t)
	resp, body := get(t, srv, "/api/now", nil)
	if resp.StatusCode != 200 || !strings.Contains(body, `"rega"`) {
		t.Errorf("/api/now: %d %s", resp.StatusCode, body)
	}
	if resp, body := get(t, srv, "/api", nil); resp.StatusCode != 200 || !strings.Contains(body, "<html") {
		t.Errorf("/api docs: %d", resp.StatusCode)
	}
	if resp, body := get(t, srv, "/api/", map[string]string{"Accept": "text/html"}); resp.StatusCode != 200 || !strings.Contains(body, "<html") {
		t.Errorf("/api/ docs: %d", resp.StatusCode)
	}
	if resp, body := get(t, srv, "/api/", map[string]string{"Accept": "application/json"}); resp.StatusCode != 200 || !strings.Contains(body, `"endpoints"`) {
		t.Errorf("/api/ json index: %d %s", resp.StatusCode, body)
	}
	if resp, _ := get(t, srv, "/now", nil); resp.StatusCode != 301 || resp.Header.Get("Location") != "/api/now" {
		t.Errorf("legacy redirect: %d %s", resp.StatusCode, resp.Header.Get("Location"))
	}
	if resp, _ := get(t, srv, "/health", nil); resp.StatusCode != 301 {
		t.Errorf("legacy /health: %d", resp.StatusCode)
	}
}

func TestStaticAndHeaders(t *testing.T) {
	srv := newSite(t)
	resp, body := get(t, srv, "/static/css/site.css?v=abc", nil)
	if resp.StatusCode != 200 || !strings.Contains(body, "--paper") {
		t.Fatalf("css: %d", resp.StatusCode)
	}
	if cc := resp.Header.Get("Cache-Control"); !strings.Contains(cc, "immutable") {
		t.Errorf("versioned asset Cache-Control = %q", cc)
	}
	if resp, _ := get(t, srv, "/static/css/site.css", nil); strings.Contains(resp.Header.Get("Cache-Control"), "immutable") {
		t.Error("unversioned asset should not be immutable")
	}
	if resp, _ := get(t, srv, "/static/fonts/EBGaramond-latin.woff2", nil); resp.StatusCode != 200 || resp.Header.Get("Content-Type") != "font/woff2" {
		t.Errorf("font: %d %s", resp.StatusCode, resp.Header.Get("Content-Type"))
	}
	if resp, _ := get(t, srv, "/static/og.png", nil); resp.StatusCode != 200 || resp.Header.Get("Content-Type") != "image/png" {
		t.Errorf("og.png: %d %s", resp.StatusCode, resp.Header.Get("Content-Type"))
	}
	if resp, _ := get(t, srv, "/static/nope.css", nil); resp.StatusCode != 404 {
		t.Errorf("missing asset: %d", resp.StatusCode)
	}
	if resp, _ := get(t, srv, "/static/", nil); resp.StatusCode != 404 {
		t.Errorf("directory listing: %d", resp.StatusCode)
	}
	resp, _ = get(t, srv, "/", nil)
	for h, want := range map[string]string{
		"Content-Security-Policy": "default-src 'self'",
		"X-Content-Type-Options":  "nosniff",
		"X-Frame-Options":         "DENY",
		"Referrer-Policy":         "strict-origin",
	} {
		if !strings.Contains(resp.Header.Get(h), want) {
			t.Errorf("%s = %q", h, resp.Header.Get(h))
		}
	}
	if resp.Header.Get("Strict-Transport-Security") != "" {
		t.Error("HSTS must not be set on plain HTTP")
	}
	resp, _ = get(t, srv, "/", map[string]string{"X-Forwarded-Proto": "https"})
	if resp.Header.Get("Strict-Transport-Security") == "" {
		t.Error("HSTS missing behind HTTPS proxy")
	}
	if !strings.Contains(resp.Header.Get("Cache-Control"), "max-age") {
		t.Errorf("page Cache-Control = %q", resp.Header.Get("Cache-Control"))
	}
}

func TestRobotsSitemap(t *testing.T) {
	srv := newSite(t)
	resp, body := get(t, srv, "/robots.txt", map[string]string{"X-Forwarded-Proto": "https", "X-Forwarded-Host": "zman.example"})
	if resp.StatusCode != 200 || !strings.Contains(body, "Sitemap: https://zman.example/sitemap.xml") {
		t.Errorf("robots: %d %s", resp.StatusCode, body)
	}
	resp, body = get(t, srv, "/sitemap.xml", map[string]string{"X-Forwarded-Proto": "https", "X-Forwarded-Host": "zman.example"})
	if resp.StatusCode != 200 || !strings.Contains(body, "<loc>https://zman.example/he/manifeste</loc>") || !strings.Contains(body, `hreflang="x-default"`) {
		t.Errorf("sitemap: %d\n%s", resp.StatusCode, body)
	}
	if n := strings.Count(body, "<url>"); n != len(Langs)*len(Pages) {
		t.Errorf("sitemap has %d urls, want %d", n, len(Langs)*len(Pages))
	}
	if resp, _ := get(t, srv, "/favicon.ico", nil); resp.StatusCode != 302 {
		t.Errorf("favicon.ico: %d", resp.StatusCode)
	}
}

func TestBaseURLConfig(t *testing.T) {
	a := api.New(clock.Fixed{}, api.WithPrefix("/api"))
	s, err := New(webfs.FS, a.Handler(), Config{BaseURL: "https://zman.test/", Version: "v1"})
	if err != nil {
		t.Fatal(err)
	}
	srv := httptest.NewServer(s)
	defer srv.Close()
	_, body := get(t, srv, "/en/api", nil)
	if !strings.Contains(body, `<link rel="canonical" href="https://zman.test/en/api">`) {
		t.Error("canonical should use BaseURL")
	}
	if !strings.Contains(body, `site.css?v=v1`) {
		t.Error("asset version should use Config.Version")
	}
}
