$(function() {

    $("#query").focus().select();
/*
    var History = window.History;

    if (!History.enabled)
        return false;

    // ajaxify links & use html5 history API

    var historyCache = {};
    historyCache[location.href] = $("#content").clone().wrap("<div>").parent().html();

    function parseQueryString() {
        var r = /([^&=]+)=?([^&]*)/g,
            d = function (s) { return decodeURIComponent(s.replace(/\+/, " ")); },
            q = window.location.search.substring(1),
            qs = {},
            e;
        while (e = r.exec(q))
            qs[d(e[1])] = d(e[2]);
        return qs;
    }
    
    // Assumes root element of content has an ID
    function replaceContent(content) {
        var $content = $(content);
        $("#" + $content.attr("id")).replaceWith($content);

        var title = $content.find("#page-title");
        if (title.length && title.text() != Globals.siteName)
            document.title = title.text() + " - " + Globals.siteName;
        else
            document.title = Globals.siteName;
        
        var qs = parseQueryString();
        $("#query").val(qs.query);

        $(window).scrollTop(0);
    }
    
    var ajaxLinks = ["#header h1 a",
                     "#projects .sort-links a",
                     ".paginated p.nav a",
                     "ul.project-list a",
                     "ul.dep-list a",
                     "ul.version-list a",
                     "#project-link"];
    $(ajaxLinks.join(", ")).live("click", function() {
        if ($(this).hasClass("inactive"))
            return false;
        History.pushState(null, null, this.href);
        return false;
    });

    $("#header form").submit(function() {
        var form = $(this),
            url = form.attr("action") + "?" + form.serialize();
        History.pushState(null, null, url);
        return false;
    });

    History.Adapter.bind(window, "statechange", function() {
        if (historyCache[location.href]) {
            replaceContent(historyCache[location.href]);
        } else {
            var params = {_: (new Date().getTime())}; // cache buster            
            $.get(location.href, params, function(data) {
                historyCache[location.href] = data;
                replaceContent(data);
            });
        }
    });
*/
});