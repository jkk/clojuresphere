$(function() {

    var topOffset = 0;

    function refreshTop() {
        $.get("/_fragments/top-projects", {offset: topOffset}, function(data) {
            $("#top-projects ul").replaceWith(data);
        });
        if (topOffset > 0)
            $("p.nav a.prev").removeClass("inactive");
        else
            $("p.nav a.prev").addClass("inactive");
    }

    $("p.nav a.next").click(function() {
        topOffset += 20;
        refreshTop();
        return false;
    });
    $("p.nav a.prev").click(function() {
        topOffset -= 20;
        if (topOffset < 0)
            topOffset = 0;
        refreshTop();
        return false;
    });

    $("p.refresh a").click(function() {
        $.get("/_fragments/random", function(data) {
            $("#random-projects ul").replaceWith(data);
        });
        return false;
    });
});