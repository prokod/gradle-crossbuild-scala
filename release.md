# Releaseing a new version
- prerequisites - issues should be created in github and should be associated with milestone v<version>
- create release/<version> branch from develop
- update build.gradle version and documentation (README.md)
- push branch and create PR to master with description `closes #<issue>, closess #<issue> ...`
- merge PR to master, do not remove release branch yet
- create release object v<version> on github
- update release object using gren `gren release --tags v<version> --override --data-source=milestones --milestone-match="{{tag_name}}"`
- before artifacts upload gradle.properties should be updated to contain sensitive credentials and signing info
- upload artifacts to maven `./gradlew clean -x check build publish`
- before uploading to gradle's plugin portal check existence of `gradle.publish.key` `gradle.publish.secret` in global gradle.properties
- If you are on WSL make sure `date` command is showing current date/time
- uploading to gradle's plugin portal `./gradelw publishPlugins`

