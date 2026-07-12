#!/bin/bash
# Dev-режим: Vite dev server на :5173 (HMR, source maps).
# Удобно для правок — но производительность здесь мерить нельзя, см. start-prod.sh.
exec "$(dirname "$0")/start-core.sh" dev
