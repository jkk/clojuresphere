$(function() {

    var History = window.History;

    if (!History.enabled)
        return false;

    // ajaxify links & use html5 history API

    var historyCache = {};
    historyCache[location.href] = $("#content").clone().wrap("<div>").parent().html();
    
    // Assumes root element of content has an ID
    function replaceContent(content) {
        var $content = $(content);
        // TODO: set page title if present
        $("#" + $content.attr("id")).replaceWith($content);
    }
    
    var ajaxLinks = ["#header h1 a",
                     ".paginated p.nav a",
                     "ul.project-list a",
                     "ul.dep-list a",
                     "ul.version-list a"];
    $(ajaxLinks.join(", ")).live("click", function() {
        if ($(this).hasClass("inactive"))
            return false;
        History.pushState(null, null, this.href);
        return false;
    });

    History.Adapter.bind(window, "statechange", function() {
        if (historyCache[location.href]) {
            replaceContent(historyCache[location.href]);
            $(window).scrollTop(0);
        } else {
            var params = {_: (new Date().getTime())}; // cache buster            
            $.get(location.href, params, function(data) {
                historyCache[location.href] = data;
                replaceContent(data);
                $(window).scrollTop(0);
            });
        }
    });
});