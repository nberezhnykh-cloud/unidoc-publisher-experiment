# Эксперимент с UniDoc Publisher

Проверяем альтернативный подход к генерации DOCX из Asciidoc:

- Вместо Asciidoctor → HTML → Pandoc → DOCX
- Используем UniDoc Publisher → FODT → DOCX

## Структура проекта

unidoc-publisher-experiment/
├── README.md
├── build-doc.main.kts           # Kotlin-скрипт сборки
├── .gitignore
├── src/
│   ├── index.adoc               # главный документ
│   └── chapters/
│       ├── common_data.adoc
│       ├── work_basis.adoc
│       └── abbreviations.adoc   # ваша таблица с сокращениями
├── templates/
│   └── template.fodt            # базовый шаблон
├── out/                         # (игнорируем, будет создаваться при сборке)
└── docs/                        # сюда будем класть результат для GitHub Pages

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
