#!/usr/bin/env bash
# Copyright (c) 2021 Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

DAR1=$(sha1sum $1)
DAR2=$(sha1sum $2)

if [ $DAR1 -eq $DAR2 ]
then
  exit 0
else
  exit 1
fi
