Hive OSS Windows / Linux release
================================

Install Java 21 and make java available on PATH.

Start
-----
Extract the program ZIP into a new directory. Run start.cmd on Windows or
sh start.sh on Linux. Open http://localhost:8080/#/setup to download the
encrypted YAML using a release ticket, then enter its password.
The management UI starts without business secrets; business starts after unlock.
Decrypted configuration remains in JVM memory.
Downloads go directly to .release/download/<UUID>/, without saving the ticket or
copying encrypted YAML. Successful business startup records the encrypted file
path in .release/download/current.txt for later offline unlocking. Local encryption
uses the same config directory; existing secret files are not migrated.

No secret YAML is included in the program directory. Normal installations use
a release ticket to download and unlock the encrypted configuration. For local
development, select a prepared YAML file in the setup page to start the business.
Wrong passwords never fall back to plaintext. Tencent is disabled in the base
configuration unless the loaded secret YAML overrides it.

Management endpoints remain under /setup; business and publishing endpoints use
/api (for example /api/categories and /api/self-release). Both contexts share
one HTTP port, but each owns its controllers and Spring MVC configuration.

Publish through the UI
----------------------
Load or unlock the secret YAML on /#/setup to start the business context first.
Then open /#/admin/release. Enter a version and bundle password, then click
Publish and confirm. The program packages the
directory, encrypts the YAML, generates the manifest and uploads all three files.
The business context must be running. New bundles require at least 12 characters.
The management machine needs release target settings and its local operator profile.
The Java SDK reads the selected AK/StsToken profile; no Aliyun CLI executable is needed.
In the source repository, package-release.cmd builds the application (skipping
tests) and runs assemble-release.cmd to prepare .release/build. Scripts and this
README are maintained together in hive-oss/src/distribution.
The program uses .release/build, .release/upload, and .release/download under its working directory.
Only a checkout with the Git source is used to assemble and publish releases.
The installed program is for running Hive and downloading its release config.
Release settings are in the JAR's application.yml; local overrides use config/application.yml.
The whole directory is packaged without filtering; keep credentials, business
data and logs outside it. Do not reassemble input while packaging is running.

The release page can generate download.ticket.json for a completed version.
Send the ticket and bundle password separately. Restart to change business
configuration.

Data and upgrades
-----------------
Persistent data is stored under data/ by default. Preserve data/ and config/.
Stop the application, back up the previous JAR, replace only the program JAR
and startup scripts, then start and verify the new program.

Configuration
-------------
Classpath defaults are overridden by config/application.yml and
secret YAML loaded explicitly through the setup UI.
This POC unlocks through the UI. Never place passwords in argv or logs.
