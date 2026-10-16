# Mardexas – Markdown Format Demo

This document showcases every Markdown formatting option supported by Mardexas.
Use it as a reference for screenshots and visual testing.

---

## Headings

### Heading 2 (H2)

#### Heading 3 (H3)

##### Heading 4 (H4)

###### Heading 5 (H5)

## Heading 1 (H1)

---

## Text Formatting

This paragraph contains **bold text**, *italic text*, and ***bold italic combined***.

You can also use __double underscores__ for bold and _single underscores_ for italic.

~~Strikethrough text~~ is supported for deletions or corrections.

`Inline code` appears in a monospaced font for short code snippets or commands.

A sentence with **bold and *italic inside*** demonstrates nested formatting.

---

## Lists

### Bulleted List

- First item
- Second item
  - Indented sub-item (level 2)
  - Another sub-item
- Third item
- Fourth item

### Numbered List

1. First step
2. Second step
   1. Sub-step A
   2. Sub-step B
3. Third step
4. Fourth step

### Mixed Lists

- Top-level bullet
  1. Numbered sub-item
  2. Numbered sub-item
- Another top-level bullet

---

## Blockquote

> This is a blockquote. It is used for quotations, excerpts, or highlighted text blocks.
>
> It can span multiple lines and may contain **bold** or *italic* text inside.

> Nested blockquote example:
> > A quote within a quote.

---

## Code Block

```markdown
# This is a fenced code block
# It preserves formatting and uses a monospaced font

function hello() {
    console.log("Hello, Mardexas!");
}
```

```python
def greet(name):
    """Greets a person by name."""
    return f"Hello, {name}!"

print(greet("World"))
```

---

## Horizontal Rule

Content above the line.

---

Content below the line.

---

## Links

[Visit the Mardexas website](https://github.com/RainerGa/Mardexas)

[Open documentation](https://example.com/docs)

---

## Images

![Mardexas logo](https://example.com/logo.png)

![Screenshot placeholder](https://example.com/screenshot.png)

---

## Tables

| Feature | Supported | Notes |
|---------|-----------|-------|
| Bold | Yes | Via toolbar or `**text**` |
| Italic | Yes | Via toolbar or `*text*` |
| Underline | Yes | Via toolbar |
| Strikethrough | Yes | Via toolbar or `~~text~~` |
| Inline Code | Yes | Via toolbar or `` `code` `` |
| Headings | Yes | H1 through H6 |
| Blockquote | Yes | Via `>` prefix |
| Bulleted List | Yes | Via `-` or `*` |
| Numbered List | Yes | Via `1.` |
| Code Block | Yes | Via fenced blocks |
| Horizontal Rule | Yes | Via `---` |
| Links | Yes | Via `[text](url)` |
| Images | Yes | Via `![alt](url)` |
| Tables | Yes | Via pipe syntax |

---

## Combination Example

Here is a paragraph that combines multiple formats:

**Important:** Please review the *quarterly report* before ~~Friday~~ next Monday. The `config.yaml` file must be updated with the new settings.

> "This project demonstrates that rich markdown editing can be both powerful and simple." — Mardexas Team

1. Read the documentation
2. Configure your environment
   - Set up your IDE
   - Install dependencies
   - Run the test suite
3. Start building

---

## End of Demo

Thank you for using Mardexas – the Rich Markdown Editor.