# Эксперимент с UniDoc Publisher

Проверяем альтернативный подход к генерации DOCX из Asciidoc:

- Вместо Asciidoctor → HTML → Pandoc → DOCX
- Используем UniDoc Publisher → FODT → DOCX

## Как запустить

```bash
# Установить Kotlin
brew install kotlin

# Установить LibreOffice
brew install --cask libreoffice

# Собрать версию ЧТЗ
DOC_TYPE=chtz kotlin build-doc.main.kts

# Собрать версию ПМИ
DOC_TYPE=pm kotlin build-doc.main.kts
