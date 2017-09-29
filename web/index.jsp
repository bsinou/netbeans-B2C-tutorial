<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>

<%-- Set session-scoped variable to track the view user is coming from.
     This is used by the language mechanism in the Controller so that
     users view the same page when switching between English and Czech. --%>
<c:set var='view' value='/index' scope='session' />

<div id="indexLeftColumn">
    <div id="welcomeText">
     <p style="font-size: larger"><fmt:message key='greeting'/></p>

        <p><fmt:message key='introText'/></p>
    </div>            
</div>
<div id="indexRightColumn">
    <c:forEach var="category" items="${categories}">
        <div class="categoryBox">
            <a href="category?${category.id}">
                <span class="categoryLabel"></span>
                <span class="categoryLabelText"><fmt:message key='${category.name}'/></span>
                <img src="${initParam.categoryImagePath}${category.name}.jpg"
                     alt="${category.name}">
            </a>
        </div>
    </c:forEach>
</div>