#!/usr/bin/env kotlin

@file:DependsOn("ru.fiddlededee:unidoc-publisher:0.9.3")
@file:DependsOn("org.jsoup:jsoup:1.17.2")

import java.io.File
import ru.fiddlededee.unidoc.publisher.AsciidocHtmlFactory
import ru.fiddlededee.unidoc.publisher.FodtConverter
import ru.fiddlededee.unidoc.publisher.adapters.AsciidoctorAdapter
import ru.fiddlededee.unidoc.publisher.model.*
import ru.fiddlededee.unidoc.publisher.style.OdtStyle
import ru.fiddlededee.unidoc.publisher.model.table.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// ============================================================
// КОНФИГУРАЦИЯ
// ============================================================

val DOC_TYPE = System.getenv("DOC_TYPE") ?: "chtz"
val OUT_DIR = File("out")
val TEMPLATE_FILE = File("templates/template.fodt")

OUT_DIR.mkdirs()
println("==> Сборка для типа документа: $DOC_TYPE")

// ============================================================
// 1. ЗАГРУЗКА И ПРЕДОБРАБОТКА ASCIIDOC
// ============================================================

if (!TEMPLATE_FILE.exists()) {
    println("Ошибка: шаблон не найден: ${TEMPLATE_FILE.absolutePath}")
    kotlin.system.exitProcess(1)
}

val mainAdoc = File("src/index.adoc").readText()
val adocWithAttributes = ":$DOC_TYPE:\n\n$mainAdoc"

println("==> Конвертация AsciiDoc -> HTML")
val html = AsciidocHtmlFactory.getHtmlFromString(adocWithAttributes)

// Сохраняем HTML для отладки
File(OUT_DIR, "debug.html").writeText(html)

// ============================================================
// 2. ПРЕОБРАЗОВАНИЕ HTML (аналог styles.lua)
// ============================================================

println("==> Предобработка HTML (применение стилей из styles.lua)")

// Парсим HTML через Jsoup для манипуляций
val jsoupDoc: Document = Jsoup.parse(html, "UTF-8")

// 2.1. Преобразование подписей таблиц: "Table 1." -> "Таблица 1 – "
jsoupDoc.select("table caption.title").forEach { caption ->
    var text = caption.text()
    val newText = text.replace(Regex("^Table (\\d+)\\.\\s+"), "Таблица $1 – ")
    if (newText != text) {
        caption.text(newText)
        caption.attr("custom-style", "_Table-caption")
    }
}

// 2.2. Преобразование подписей рисунков: "Figure 1." -> "Рисунок 1 – "
jsoupDoc.select("div.imageblock div.title").forEach { title ->
    var text = title.text()
    val newText = text.replace(Regex("^Figure (\\d+)\\.\\s+"), "Рисунок $1 – ")
    if (newText != text) {
        title.text(newText)
    }
}

// 2.3. Применение CSS классов к параграфам (аналог Div-обработки из styles.lua)
val classToStyleMap = mapOf(
    "warning" to "WarningText",
    "note" to "NoteText",
    "custom-paragraph" to "CustomParagraph",
    "CustomText" to "_text",
    "CustomList1" to "_list-1-lvl",
    "CustomList2" to "_list-2-lvl",
    "CustomList3" to "_list-3-lvl",
    "CustomList4" to "_list-4-lvl",
    "PictureName" to "_Picture-name",
    "Picture" to "_Picture"
)

jsoupDoc.select("div[class]").forEach { div ->
    val classes = div.className().split(" ")
    for (cssClass in classes) {
        classToStyleMap[cssClass]?.let { styleName ->
            div.attr("custom-style", styleName)
            // Также применяем к вложенным параграфам
            div.select("p").forEach { p ->
                p.attr("custom-style", styleName)
            }
        }
    }
}

// 2.4. Обработка подсветки (highlight)
jsoupDoc.select("span.highlight").forEach { span ->
    span.attr("custom-style", "HighlightInline")
}

val processedHtml = jsoupDoc.html()

// ============================================================
// 3. СТИЛИ ДЛЯ ТАБЛИЦ (ODT STYLES)
// ============================================================

println("==> Настройка стилей таблиц")

val customStyles = listOf(
    // Стиль для заголовка таблицы (_Table-heading)
    OdtStyle { node ->
        if (node !is TableCell) return@OdtStyle
        val parentRow = node.parent
        val parentTable = parentRow?.parent
        // Если это первая строка таблицы (заголовок)
        if (parentTable is Table && parentTable.rows.indexOf(parentRow) == 0) {
            node.tableCellProperties {
                attribute("fo:background-color", "#D9D9D9")
                attribute("fo:font-weight", "bold")
                attribute("fo:text-align", "center")
                attribute("fo:border", "0.5pt solid #000000")
                attribute("fo:padding", "2mm")
            }
        }
    },
    
    // Стиль для обычного текста в таблице (_Table_text)
    OdtStyle { node ->
        if (node !is TableCell) return@OdtStyle
        // Пропускаем заголовки
        val parentRow = node.parent
        val parentTable = parentRow?.parent
        if (parentTable is Table && parentTable.rows.indexOf(parentRow) == 0) {
            return@OdtStyle
        }
        node.tableCellProperties {
            attribute("fo:border", "0.5pt solid #000000")
            attribute("fo:padding", "2mm")
        }
    },
    
    // Зебра для чётных строк
    OdtStyle { node ->
        if (node !is TableRow) return@OdtStyle
        val parentTable = node.parent
        if (parentTable is Table) {
            val rowIndex = parentTable.rows.indexOf(node)
            // Чётные строки (индексы 1, 3, 5...)
            if (rowIndex % 2 == 1 && rowIndex > 0) {
                node.cells.forEach { cell ->
                    cell.tableCellProperties {
                        attribute("fo:background-color", "#F2F2F2")
                    }
                }
            }
        }
    },
    
    // Стиль для подписи таблицы (_Table-caption)
    OdtStyle { node ->
        if (node !is Paragraph) return@OdtStyle
        if (node.text.startsWith("Таблица")) {
            node.paragraphProperties {
                attribute("fo:font-style", "italic")
                attribute("fo:text-align", "center")
                attribute("fo:margin-top", "2mm")
                attribute("fo:margin-bottom", "2mm")
            }
        }
    },
    
    // Стиль для маркированных списков 1 уровня в таблицах (_Table_list-dash-1-lvl)
    OdtStyle { node ->
        when (node) {
            is BulletList -> {
                node.listItemProperties {
                    attribute("fo:margin-left", "5mm")
                }
            }
            is ListItem -> {
                node.paragraphProperties {
                    attribute("fo:margin-left", "3mm")
                }
            }
        }
    },
    
    // Стиль для нумерованных списков в таблицах (_Table_text-num-1-lvl)
    OdtStyle { node ->
        when (node) {
            is OrderedList -> {
                node.listItemProperties {
                    attribute("fo:margin-left", "5mm")
                }
            }
        }
    },
    
    // Стиль для курсива в таблицах (_Table_curv)
    OdtStyle { node ->
        if (node is Text) {
            if (node.text.contains("*") || node.text.contains("_")) {
                // В Asciidoc *текст* или _текст_ — это курсив
                node.textProperties {
                    attribute("fo:font-style", "italic")
                }
            }
        }
    }
)

// ============================================================
// 4. ОБРАБОТКА СПИСКОВ В ТАБЛИЦАХ (AST трансформация)
// ============================================================

println("==> Парсинг HTML в AST с применением стилей")

// Расширенная AST трансформация для обработки списков в таблицах
class TableListTransformer : FodtConverter.() -> Unit = {
    // Здесь можно добавить дополнительную трансформацию AST
    // для корректной обработки вложенных списков
}

val fodtGenerator = FodtConverter()
    .adaptWith(AsciidoctorAdapter())
    .template(TEMPLATE_FILE.readText())
    .html(processedHtml)
    .odtStyleList(customStyles)
    .parse()
    .ast2fodt()

// ============================================================
// 5. ПОСТ-ОБРАБОТКА FODT (дополнительные стили)
// ============================================================

println("==> Пост-обработка FODT")

var fodtContent = fodtGenerator.fodt()

// Добавляем стили для списков в таблицах, если их нет
val tableListStyles = """
    <style:style style:name="TableList1" style:family="paragraph">
        <style:paragraph-properties fo:margin-left="5mm" fo:margin-top="0mm" fo:margin-bottom="0mm"/>
        <style:text-properties fo:font-size="11pt"/>
    </style:style>
    <style:style style:name="TableList2" style:family="paragraph">
        <style:paragraph-properties fo:margin-left="10mm" fo:margin-top="0mm" fo:margin-bottom="0mm"/>
        <style:text-properties fo:font-size="11pt"/>
    </style:style>
"""

// Вставляем стили в FODT, если их нет
if (!fodtContent.contains("TableList1")) {
    fodtContent = fodtContent.replace(
        "</office:styles>",
        "$tableListStyles</office:styles>"
    )
}

// ============================================================
// 6. СОХРАНЕНИЕ FODT
// ============================================================

val fodtOutputFile = File(OUT_DIR, "$DOC_TYPE.fodt")
fodtOutputFile.writeText(fodtContent)
println("==> FODT сохранён: ${fodtOutputFile.absolutePath}")

// ============================================================
// 7. КОНВЕРТАЦИЯ В DOCX
// ============================================================

val sofficeCmd = listOf(
    "/Applications/LibreOffice.app/Contents/MacOS/soffice",
    "--headless",
    "--convert-to", "docx",
    "--outdir", OUT_DIR.absolutePath,
    fodtOutputFile.absolutePath
)

println("==> Конвертация в DOCX")
val process = ProcessBuilder(sofficeCmd).start()
val exitCode = process.waitFor()

if (exitCode != 0) {
    val error = process.errorStream.bufferedReader().readText()
    println("Ошибка конвертации: $error")
    println("Попробуйте сконвертировать вручную:")
    println("  soffice --headless --convert-to docx --outdir ${OUT_DIR.absolutePath} ${fodtOutputFile.absolutePath}")
} else {
    println("==> DOCX сохранён: ${File(OUT_DIR, "$DOC_TYPE.docx").absolutePath}")
}

// ============================================================
// 8. ФИНАЛЬНЫЙ ВЫВОД
// ============================================================

println("==> Готово!")
println("   Файлы в папке: ${OUT_DIR.absolutePath}")
println("   - $DOC_TYPE.fodt (промежуточный)")
println("   - $DOC_TYPE.docx (результат)")