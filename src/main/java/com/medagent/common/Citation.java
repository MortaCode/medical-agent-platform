package com.medagent.common;

/**
 * 循证溯源引用：文献/说明书来源 + 页码/章节 + 在线链接 + 摘录原文。
 */
public class Citation {

    private String source;   // 文献/说明书名称
    private String page;     // 页码或章节
    private String url;      // 在线链接
    private String excerpt;  // 说明书/文献原文摘录

    public Citation() {
    }

    public Citation(String source, String page, String url, String excerpt) {
        this.source = source;
        this.page = page;
        this.url = url;
        this.excerpt = excerpt;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPage() { return page; }
    public void setPage(String page) { this.page = page; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getExcerpt() { return excerpt; }
    public void setExcerpt(String excerpt) { this.excerpt = excerpt; }

    @Override
    public String toString() {
        return source + (page != null && !page.isBlank() ? " p" + page : "")
                + (url != null && !url.isBlank() ? " (" + url + ")" : "");
    }
}
