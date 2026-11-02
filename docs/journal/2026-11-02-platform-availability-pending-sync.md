# 2 November — platform identity and play location (pending vault sync)

Purpose: correct PlayStation tile/source identity, Xbox cloud/Game Pass source membership, and Local/Cloud play-location classification while preserving launch IDs, history and editions. See G1_SETUP_AND_RECOVERY.md's latest section for rules, rationale and limits.

Implemented shared platform/availability helpers, PlayStation vector icon/source tab, Xbox cloud URL identification, current GFN/saved host capability and reactive filters, grouped-copy availability and source/hero/detail labels. Source filters distinguish identity from remote location; Lutris host installs remain remote. No network or power setting changes, no games started.

Initial verification: TypeScript passed;32 distinct focused unit tests;2 Chromium UI tests including live-rendered badge/filter fixture and1280x800 details regression. Full unit suite, local build/install/artifact/profile verification and refreshed private backup remain pending at this source checkpoint. Existing182329 snapshot preserved. USB absent; vault sync pending.
