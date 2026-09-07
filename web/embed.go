// Package webfs embarque les fichiers du site dans le binaire.
package webfs

import "embed"

// FS contient templates/, content/, i18n/, static/ et examples/.
//
//go:embed templates content i18n static all:examples
var FS embed.FS
