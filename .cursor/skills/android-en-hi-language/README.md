# Install in ApniPost (or any Android app)

## Best option: Cursor Skill

1. Copy this whole folder into the other project:

```text
ApniPost/.cursor/skills/android-en-hi-language/
  SKILL.md
  README.md
```

2. Open ApniPost in Cursor.
3. Tell the agent:

```text
Read and follow .cursor/skills/android-en-hi-language/SKILL.md
Add English/Hindi language choice exactly like Emaan Wallpapers.
Do not break existing features. Run unit tests and make them pass.
```

The agent will build AppLanguage, UI pickers, strings, manifest, smooth switch, and tests from that file.

## Alternative

If the project has no `.cursor/skills`, put `SKILL.md` at:

```text
ApniPost/LANGUAGE_SUPPORT.md
```

Then say: “Implement LANGUAGE_SUPPORT.md fully.”
