package iers

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

// Remote est un cache distant partagé entre instances (par exemple un
// objet GCS), consulté après le disque et avant le réseau IERS.
type Remote interface {
	Get(ctx context.Context) (data []byte, modified time.Time, err error)
	Put(ctx context.Context, data []byte) error
}

// ErrRemoteMissing signale un objet absent du cache distant.
var ErrRemoteMissing = errors.New("iers: remote cache object missing")

// GCS est un cache distant sur Google Cloud Storage, sans SDK : l'API
// JSON en HTTP, authentifiée par le jeton du serveur de métadonnées
// (Cloud Run, GCE) ou par un jeton fourni.
type GCS struct {
	Bucket string
	Object string
	HTTP   *http.Client

	// Endpoints, surchargeables pour les tests.
	StorageURL  string // défaut https://storage.googleapis.com
	MetadataURL string // défaut http://metadata.google.internal

	mu     sync.Mutex
	token  string
	expiry time.Time
}

// NewGCS crée un cache GCS à partir de "bucket/chemin/objet".
func NewGCS(spec string) (*GCS, error) {
	bucket, object, ok := strings.Cut(strings.TrimPrefix(spec, "gs://"), "/")
	if !ok || bucket == "" || object == "" {
		return nil, fmt.Errorf("iers: invalid GCS spec %q, want bucket/object", spec)
	}
	return &GCS{
		Bucket:      bucket,
		Object:      object,
		HTTP:        &http.Client{Timeout: 60 * time.Second},
		StorageURL:  "https://storage.googleapis.com",
		MetadataURL: "http://metadata.google.internal",
	}, nil
}

func (g *GCS) accessToken(ctx context.Context) (string, error) {
	g.mu.Lock()
	defer g.mu.Unlock()
	if g.token != "" && time.Until(g.expiry) > time.Minute {
		return g.token, nil
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet,
		g.MetadataURL+"/computeMetadata/v1/instance/service-accounts/default/token", nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("Metadata-Flavor", "Google")
	resp, err := g.HTTP.Do(req)
	if err != nil {
		return "", fmt.Errorf("metadata token: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("metadata token: HTTP %s", resp.Status)
	}
	var tok struct {
		AccessToken string `json:"access_token"`
		ExpiresIn   int    `json:"expires_in"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&tok); err != nil {
		return "", fmt.Errorf("metadata token: %w", err)
	}
	g.token = tok.AccessToken
	g.expiry = time.Now().Add(time.Duration(tok.ExpiresIn) * time.Second)
	return g.token, nil
}

// Get télécharge l'objet et renvoie sa date de modification.
func (g *GCS) Get(ctx context.Context) ([]byte, time.Time, error) {
	tok, err := g.accessToken(ctx)
	if err != nil {
		return nil, time.Time{}, err
	}
	u := fmt.Sprintf("%s/storage/v1/b/%s/o/%s?alt=media", g.StorageURL, url.PathEscape(g.Bucket), url.PathEscape(g.Object))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, u, nil)
	if err != nil {
		return nil, time.Time{}, err
	}
	req.Header.Set("Authorization", "Bearer "+tok)
	resp, err := g.HTTP.Do(req)
	if err != nil {
		return nil, time.Time{}, err
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusNotFound {
		return nil, time.Time{}, ErrRemoteMissing
	}
	if resp.StatusCode != http.StatusOK {
		return nil, time.Time{}, fmt.Errorf("gcs get: HTTP %s", resp.Status)
	}
	data, err := io.ReadAll(io.LimitReader(resp.Body, 64<<20))
	if err != nil {
		return nil, time.Time{}, err
	}
	mod := time.Now()
	if lm := resp.Header.Get("Last-Modified"); lm != "" {
		if t, err := http.ParseTime(lm); err == nil {
			mod = t
		}
	}
	return data, mod, nil
}

// Put écrase l'objet.
func (g *GCS) Put(ctx context.Context, data []byte) error {
	tok, err := g.accessToken(ctx)
	if err != nil {
		return err
	}
	u := fmt.Sprintf("%s/upload/storage/v1/b/%s/o?uploadType=media&name=%s", g.StorageURL, url.PathEscape(g.Bucket), url.QueryEscape(g.Object))
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, u, bytes.NewReader(data))
	if err != nil {
		return err
	}
	req.Header.Set("Authorization", "Bearer "+tok)
	req.Header.Set("Content-Type", "text/plain")
	resp, err := g.HTTP.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode/100 != 2 {
		return fmt.Errorf("gcs put: HTTP %s", resp.Status)
	}
	return nil
}
