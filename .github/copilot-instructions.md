# Copilot Instructions — JL-Mod

## Philosophy

- **KISS + DRY**: Simple, modular, non-repetitive code. No clever tricks that trade readability for brevity.
- **No rambling**: Responses must be concise. Skip preamble, philosophical reasoning, and filler text. Output code and targeted comments only.

## Code Style

### Structure
- **Single Responsibility**: Every method/class does exactly one thing.
- **Guard clauses first**: Use early returns to eliminate deep nesting. Never write a Pyramid of Doom.
- **Extract helpers**: Any block of logic that needs a comment deserves its own method with a descriptive name.
- **No dead code**: Remove unused imports, unused variables, and commented-out blocks before finishing.

### Naming
- Descriptive over short: `getUserProfile()` not `getData()`, `isKeyboardVisible` not `flag`.
- Boolean variables/methods must read as predicates: `isActive`, `hasPermission`, `isDpadRing()`.

## Error Handling

- **Never swallow exceptions** silently. At minimum, log the exception with context.
- Log structured data alongside errors: *what* failed, *where*, and *what inputs* caused it.
- Throw descriptive custom exceptions when encountering unexpected state, so the failure site is immediately obvious.

```java
// BAD
try { ... } catch (IOException e) { e.printStackTrace(); }

// GOOD
try { ... } catch (IOException e) {
    Log.e(TAG, "loadDpadSettings: failed to read layout file at " + saveFile, e);
    throw new RuntimeException("Cannot load D-pad layout", e);
}
```

## Output Format

- **Surgical edits only**: When modifying existing code, show only the changed method/block. Never rewrite the full file unless explicitly asked.
- Use `replace_string_in_file` or `multi_replace_string_in_file` for edits; include 3–5 lines of unchanged context above and below each change.
- Multiple independent edits → single `multi_replace_string_in_file` call, not sequential calls.
- Do **not** create Markdown documentation files unless the user explicitly requests them.

## Android / Java Specifics

- `volatile` for any static field read across threads (e.g., game-loop fields).
- Guard all `ProfileModel` numeric fields against zero/default before use (avoid epoch-zero or division-by-zero bugs).
- Always call `overlayView.postInvalidate()` after any state change that affects drawing.
- Save user config via `ProfilesManager.saveConfig(settings)` — never mutate `ProfileModel` without persisting.
- When adding a new layout type (e.g., `TYPE_DPAD_RING`), update **all** guards that gate on existing types (`TYPE_CUSTOM`, `TYPE_JOYSTICK`) consistently: `saveLayout`, `readLayout`, `setLayout`, `getKeyNames`, `getKeysVisibility`, `setKeysVisibility`.

## Project Structure

```
app/src/main/java/
  javax/microedition/lcdui/keyboard/   ← VirtualKeyboard, KeyMapper
  javax/microedition/shell/            ← MicroLoader, MidletSystem, MicroActivity
  ru/playsoftware/j2meloader/config/   ← ProfileModel, ConfigActivity, ProfilesManager
app/src/main/res/
  values/strings.xml                   ← all user-visible strings
  values/arrays.xml                    ← spinner entries (e.g. PREF_VK_TYPE_ENTRIES)
dexlib/                                ← ASM bytecode transformer (AndroidMethodVisitor)
```
