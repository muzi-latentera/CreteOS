# CreteOS Windows data and services

This Windows fork inherits LiftOff's integrations. Account connections are optional. Local settings, discovered libraries, collections and cached art/media are stored under `%LOCALAPPDATA%\CreteOS`. WebView data uses the `io.latent.creteos.windows` application identity. Supported account tokens use the Windows credential store through the inherited keyring integration, with separate CreteOS namespaces.

Depending on enabled features, the launcher contacts Steam, Microsoft/Xbox, Spotify, SteamGridDB, cloud gaming/catalog services and media sources. Those services receive the requests needed for their features and have their own privacy policies. Microsoft/Steam sign-in, Spotify playback and artwork/download functions retain upstream behavior; this fork has not independently audited every integration.

Home’s optional-content ‘What’s new’ rail requests the public Reddit r/Games + r/pcgaming RSS feed, without account authentication. Successful feed entries may load thumbnails from Reddit image hosts. Articles open in your default browser. Feed results, including failures, are cached in memory for 30 minutes; the rail is hidden when no entries are available.

Manual update checks contact GitHub for the CreteOS repository's releases and only select tags prefixed `windows-v`. Automatic update checks default to off. The Xbox cloud catalog refresh currently reads LiftOff's upstream GitHub data file. No CreteOS analytics service was added.

Disconnect accounts through Settings before removing local data. Factory reset removes launcher data, so export/back up configuration if needed. GPD tools and AnyFSE run as separate applications and retain their own settings and data handling. The profile generator writes the local executable paths you supply into its output; review that file before sharing it.
