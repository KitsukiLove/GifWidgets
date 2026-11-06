# GifWidgets: R8 rules (release build with minify + shrinkResources).
#
# Nothing needs to be kept on purpose:
# - Activities, receivers, the tile service and the widget providers are kept automatically from the manifest.
# - The app uses no reflection of its own and no serialization library (JSON is read with org.json from the platform).
# - Enum names are stored as text in meta.json / settings.json; R8 does not rename enum constants.
# - Compose and AndroidX ship their own consumer rules.
#
# VERIFY: build the release variant, install it and check the library, the editor, a widget and Settings.
# If something crashes only in release, add a -keep rule for the class named in the stack trace and note it in DECISIONS.md.
