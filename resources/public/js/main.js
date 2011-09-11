$(function() {

    // ajaxify links & use html5 history API
    if (!!(window.history && history.pushState)) {
        var historyCache = {};
        historyCache[location.href] = $("#content").clone().wrap("<div>").parent().html();

        // Assumes root element of content has an ID
        function replaceContent(content) {
            var $content = $(content);
            $("#" + $content.attr("id")).replaceWith($content);
        }

        $(".paginated p.nav a").live("click", function() {
            if ($(this).hasClass("inactive"))
                return false;
            var href = this.href;
            history.pushState(null, null, href);
            $.get(href, function(data) {
                historyCache[href] = data;
                replaceContent(data);
                $(window).scrollTop(0);
            });
            return false;
        });

        $(window).bind("popstate", function() {
            // FIXME: in chrome, this always runs on page load
            if (historyCache[location.href]) {
                replaceContent(historyCache[location.href]);
                $(window).scrollTop(0);
            }
        });
    }
});