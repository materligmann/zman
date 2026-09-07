// Package iers lit les données d'orientation de la Terre publiées par
// l'IERS (fichier finals.all / finals2000A.all) et fournit DUT1 = UT1 − UTC
// à un instant donné, par interpolation linéaire.
//
// C'est, avec pkg/clock, le seul endroit du programme où la seconde SI et
// le calendrier des nations (via le MJD) existent.
package iers

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Record est une ligne de finals.all réduite à ce qui nous intéresse.
type Record struct {
	MJD       float64 // date julienne modifiée (UTC)
	DUT1      float64 // UT1 − UTC, en secondes
	Predicted bool    // 'P' = prédiction du Bulletin A, 'I' = mesure
}

// Table est un ensemble de records triés par MJD.
type Table struct {
	recs []Record
}

// ErrNoData est renvoyée quand aucune donnée n'est disponible.
var ErrNoData = errors.New("iers: no DUT1 data")

// Parse lit un fichier finals.all (colonnes fixes, cf. readme.finals de
// l'IERS). Les lignes sans valeur UT1−UTC (prédictions lointaines) sont
// ignorées.
func Parse(r io.Reader) (*Table, error) {
	sc := bufio.NewScanner(r)
	sc.Buffer(make([]byte, 0, 256), 1<<16)
	var recs []Record
	lineNo := 0
	for sc.Scan() {
		lineNo++
		line := sc.Text()
		rec, ok, err := parseLine(line)
		if err != nil {
			return nil, fmt.Errorf("iers: line %d: %w", lineNo, err)
		}
		if ok {
			recs = append(recs, rec)
		}
	}
	if err := sc.Err(); err != nil {
		return nil, err
	}
	if len(recs) == 0 {
		return nil, ErrNoData
	}
	sort.SliceStable(recs, func(i, j int) bool { return recs[i].MJD < recs[j].MJD })
	return &Table{recs: recs}, nil
}

// parseLine renvoie (record, true, nil) pour une ligne exploitable,
// (_, false, nil) pour une ligne sans UT1−UTC, et une erreur si la ligne
// est manifestement corrompue.
func parseLine(line string) (Record, bool, error) {
	if len(line) < 68 {
		return Record{}, false, nil
	}
	mjdField := strings.TrimSpace(line[7:15])
	if mjdField == "" {
		return Record{}, false, nil
	}
	mjd, err := strconv.ParseFloat(mjdField, 64)
	if err != nil {
		return Record{}, false, fmt.Errorf("bad MJD %q", mjdField)
	}
	flag := line[57]
	dut1Field := strings.TrimSpace(line[58:68])
	if dut1Field == "" {
		return Record{}, false, nil
	}
	dut1, err := strconv.ParseFloat(dut1Field, 64)
	if err != nil {
		return Record{}, false, fmt.Errorf("bad UT1-UTC %q", dut1Field)
	}
	switch flag {
	case 'I', 'P':
	default:
		return Record{}, false, fmt.Errorf("bad UT1-UTC flag %q", string(flag))
	}
	return Record{MJD: mjd, DUT1: dut1, Predicted: flag == 'P'}, true, nil
}

// Len renvoie le nombre de records.
func (t *Table) Len() int {
	if t == nil {
		return 0
	}
	return len(t.recs)
}

// MeasuredCount renvoie le nombre de records mesurés (flag I).
func (t *Table) MeasuredCount() int {
	if t == nil {
		return 0
	}
	n := 0
	for _, r := range t.recs {
		if !r.Predicted {
			n++
		}
	}
	return n
}

// First et Last renvoient les MJD extrêmes de la table.
func (t *Table) First() float64 { return t.recs[0].MJD }
func (t *Table) Last() float64  { return t.recs[len(t.recs)-1].MJD }

// LastMeasuredMJD renvoie le MJD de la dernière valeur mesurée (non
// prédite), ou 0 s'il n'y en a aucune.
func (t *Table) LastMeasuredMJD() float64 {
	if t == nil {
		return 0
	}
	for i := len(t.recs) - 1; i >= 0; i-- {
		if !t.recs[i].Predicted {
			return t.recs[i].MJD
		}
	}
	return 0
}

// DUT1 renvoie UT1−UTC (secondes) interpolé linéairement à mjd, et un
// booléen indiquant si la valeur repose sur une prédiction (ou sur une
// extrapolation hors de la table, traitée comme prédiction).
func (t *Table) DUT1(mjd float64) (dut1 float64, predicted bool, err error) {
	if t == nil || len(t.recs) == 0 {
		return 0, true, ErrNoData
	}
	recs := t.recs
	i := sort.Search(len(recs), func(i int) bool { return recs[i].MJD >= mjd })
	switch {
	case i == 0:
		r := recs[0]
		return r.DUT1, r.Predicted || mjd < r.MJD, nil
	case i == len(recs):
		r := recs[len(recs)-1]
		return r.DUT1, true, nil
	}
	a, b := recs[i-1], recs[i]
	if b.MJD == a.MJD {
		return b.DUT1, b.Predicted, nil
	}
	f := (mjd - a.MJD) / (b.MJD - a.MJD)
	v := a.DUT1 + f*(b.DUT1-a.DUT1)
	// Saut de seconde intercalaire entre a et b : DUT1 change brutalement
	// de ~1 s. On ne l'interpole pas, on prend le côté le plus proche.
	if d := b.DUT1 - a.DUT1; d > 0.5 || d < -0.5 {
		if f < 0.5 {
			v = a.DUT1
		} else {
			v = b.DUT1
		}
	}
	return v, a.Predicted || b.Predicted, nil
}

// Status décrit l'état du client pour /health.
type Status struct {
	Loaded          bool
	Records         int
	MeasuredRecords int
	Source          string  // "disk", "network", "none"
	CacheAgeDays    float64 // âge du fichier en cache (jours)
	LastMeasuredMJD float64
	LastError       string
}

// Client télécharge finals.all, le met en cache sur disque et le
// rafraîchit périodiquement en arrière-plan.
type Client struct {
	URLs      []string
	CachePath string
	Refresh   time.Duration
	HTTP      *http.Client
	Logf      func(format string, args ...any)
	Remote    Remote // cache distant optionnel (GCS)

	mu        sync.RWMutex
	table     *Table
	source    string
	fetchedAt time.Time
	lastErr   error
}

// Option configure un Client.
type Option func(*Client)

// WithURLs fixe la liste des URL à essayer, dans l'ordre.
func WithURLs(urls ...string) Option { return func(c *Client) { c.URLs = urls } }

// WithRefresh fixe l'intervalle de rafraîchissement.
func WithRefresh(d time.Duration) Option { return func(c *Client) { c.Refresh = d } }

// WithHTTPClient fixe le client HTTP.
func WithHTTPClient(h *http.Client) Option { return func(c *Client) { c.HTTP = h } }

// WithLogger fixe la fonction de log.
func WithLogger(f func(string, ...any)) Option { return func(c *Client) { c.Logf = f } }

// WithRemote branche un cache distant partagé.
func WithRemote(r Remote) Option { return func(c *Client) { c.Remote = r } }

// DefaultURLs sont les sources publiques de finals.all, essayées dans l'ordre.
var DefaultURLs = []string{
	"https://datacenter.iers.org/data/9/finals2000A.all",
	"https://maia.usno.navy.mil/ser7/finals.all",
}

// NewClient crée un client dont le cache disque est cachePath.
func NewClient(cachePath string, opts ...Option) *Client {
	c := &Client{
		URLs:      DefaultURLs,
		CachePath: cachePath,
		Refresh:   7 * 24 * time.Hour,
		HTTP:      &http.Client{Timeout: 2 * time.Minute},
		Logf:      func(string, ...any) {},
	}
	for _, o := range opts {
		o(c)
	}
	return c
}

// Table renvoie la table courante (peut être nil si rien n'est chargé).
func (c *Client) Table() *Table {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.table
}

// Status renvoie l'état courant.
func (c *Client) Status() Status {
	c.mu.RLock()
	defer c.mu.RUnlock()
	s := Status{Source: "none"}
	if c.table != nil {
		s.Loaded = true
		s.Records = c.table.Len()
		s.MeasuredRecords = c.table.MeasuredCount()
		s.Source = c.source
		s.LastMeasuredMJD = c.table.LastMeasuredMJD()
		s.CacheAgeDays = time.Since(c.fetchedAt).Hours() / 24
	}
	if c.lastErr != nil {
		s.LastError = c.lastErr.Error()
	}
	return s
}

// LoadDisk charge le cache disque, s'il existe.
func (c *Client) LoadDisk() error {
	f, err := os.Open(c.CachePath)
	if err != nil {
		return err
	}
	defer f.Close()
	st, err := f.Stat()
	if err != nil {
		return err
	}
	t, err := Parse(f)
	if err != nil {
		return err
	}
	c.mu.Lock()
	c.table, c.source, c.fetchedAt = t, "disk", st.ModTime()
	c.mu.Unlock()
	c.Logf("iers: loaded %d records from %s", t.Len(), c.CachePath)
	return nil
}

// LoadRemote charge le cache distant, s'il est configuré et contient un
// objet. Il n'est utilisé que s'il est plus récent que ce qui est déjà
// chargé.
func (c *Client) LoadRemote(ctx context.Context) error {
	if c.Remote == nil {
		return ErrRemoteMissing
	}
	data, mod, err := c.Remote.Get(ctx)
	if err != nil {
		return err
	}
	t, err := Parse(strings.NewReader(string(data)))
	if err != nil {
		return err
	}
	c.mu.Lock()
	if c.table != nil && !mod.After(c.fetchedAt) {
		c.mu.Unlock()
		return nil
	}
	c.table, c.source, c.fetchedAt = t, "remote", mod
	c.mu.Unlock()
	if err := writeAtomic(c.CachePath, data); err != nil {
		c.Logf("iers: cannot write cache %s: %v", c.CachePath, err)
	}
	c.Logf("iers: loaded %d records from remote cache", t.Len())
	return nil
}

// Fetch télécharge le fichier depuis la première URL qui répond, le
// parse, l'installe en mémoire et l'écrit dans les caches.
func (c *Client) Fetch(ctx context.Context) error {
	var errs []error
	for _, u := range c.URLs {
		body, err := c.download(ctx, u)
		if err != nil {
			errs = append(errs, fmt.Errorf("%s: %w", u, err))
			continue
		}
		t, err := Parse(strings.NewReader(string(body)))
		if err != nil {
			errs = append(errs, fmt.Errorf("%s: %w", u, err))
			continue
		}
		if err := writeAtomic(c.CachePath, body); err != nil {
			c.Logf("iers: cannot write cache %s: %v", c.CachePath, err)
		}
		if c.Remote != nil {
			if err := c.Remote.Put(ctx, body); err != nil {
				c.Logf("iers: cannot write remote cache: %v", err)
			}
		}
		c.mu.Lock()
		c.table, c.source, c.fetchedAt, c.lastErr = t, "network", time.Now(), nil
		c.mu.Unlock()
		c.Logf("iers: fetched %d records from %s", t.Len(), u)
		return nil
	}
	err := errors.Join(errs...)
	c.mu.Lock()
	c.lastErr = err
	c.mu.Unlock()
	return err
}

func (c *Client) download(ctx context.Context, u string) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "zman/1 (jewish-time server)")
	resp, err := c.HTTP.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("HTTP %s", resp.Status)
	}
	return io.ReadAll(io.LimitReader(resp.Body, 64<<20))
}

// NeedsRefresh indique si le cache est absent ou plus vieux que Refresh.
func (c *Client) NeedsRefresh() bool {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.table == nil || time.Since(c.fetchedAt) > c.Refresh
}

// Load charge le cache disque, puis le cache distant, puis télécharge si
// tout est absent ou périmé. Une erreur réseau n'est pas fatale si un
// cache existe.
func (c *Client) Load(ctx context.Context) error {
	if err := c.LoadDisk(); err != nil && !errors.Is(err, os.ErrNotExist) {
		c.Logf("iers: cache unreadable: %v", err)
	}
	if c.NeedsRefresh() && c.Remote != nil {
		if err := c.LoadRemote(ctx); err != nil && !errors.Is(err, ErrRemoteMissing) {
			c.Logf("iers: remote cache unreadable: %v", err)
		}
	}
	if !c.NeedsRefresh() {
		return nil
	}
	if err := c.Fetch(ctx); err != nil {
		if c.Table() != nil {
			c.Logf("iers: refresh failed, keeping cached data: %v", err)
			return nil
		}
		return err
	}
	return nil
}

// Start lance le rafraîchissement périodique jusqu'à annulation de ctx.
func (c *Client) Start(ctx context.Context) {
	go func() {
		tick := time.NewTicker(c.Refresh)
		defer tick.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case <-tick.C:
				if err := c.Fetch(ctx); err != nil {
					c.Logf("iers: background refresh failed: %v", err)
				}
			}
		}
	}()
}

func writeAtomic(path string, data []byte) error {
	if path == "" {
		return nil
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		return err
	}
	tmp, err := os.CreateTemp(filepath.Dir(path), ".finals-*.tmp")
	if err != nil {
		return err
	}
	tmpName := tmp.Name()
	if _, err := tmp.Write(data); err != nil {
		tmp.Close()
		os.Remove(tmpName)
		return err
	}
	if err := tmp.Close(); err != nil {
		os.Remove(tmpName)
		return err
	}
	_ = os.Chmod(tmpName, 0o644)
	return os.Rename(tmpName, path)
}
