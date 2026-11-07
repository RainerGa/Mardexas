/** External JPMS consumer: no add-exports, add-opens or access to editor internals. */
module mardexas.integration.demos {
    requires transitive RichMarkDownEditor;
    exports de.richmarkdown.demo;
}
