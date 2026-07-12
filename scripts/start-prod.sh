#!/bin/bash
# Прод-режим: `vite build` + `vite preview` на :4173.
# Ровно тот бандл, который запекается в офлайн-jar (см. build-offline-jar.sh) —
# здесь и надо профилировать реальную производительность.
exec "$(dirname "$0")/start-core.sh" prod
