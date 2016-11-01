<%--
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
--%><!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
<%@tag import="com.serotonin.m2m2.module.ModuleRegistry"%>
<%@tag import="com.serotonin.m2m2.Common"%>
<%@include file="/WEB-INF/tags/decl.tagf"%>
<%@ taglib prefix="page" tagdir="/WEB-INF/tags/page" %>
<%@attribute name="styles" fragment="true" %>
<%@attribute name="dwr" rtexprvalue="true" %>
<%@attribute name="js" %>
<%@attribute name="css" %>
<%@attribute name="onload" %>
<%@attribute name="showHeader" %>
<%@attribute name="showToolbar" %>


<c:set var="theme">claro</c:set>
<%-- <c:set var="theme">nihilo</c:set> --%>
<%-- <c:set var="theme">soria</c:set> --%>
<%-- <c:set var="theme">tundra</c:set> --%>
<!-- To allow pages to show/hide toolbar via url param even if page doesn't pass in the parameter -->
<c:choose>
    <c:when test="${empty showHeader}"><c:set var="showHeader">${param.showHeader}</c:set></c:when>
</c:choose>
<c:choose>
    <c:when test="${empty showToolbar}"><c:set var="showToolbar">${param.showToolbar}</c:set></c:when>
</c:choose>
<c:choose>
    <c:when test="${empty showFooter}"><c:set var="showFooter">${param.showFooter}</c:set></c:when>
</c:choose>
<html>
<head>
  <title><c:choose>
    <c:when test="${!empty instanceDescription}">${instanceDescription}</c:when>
    <c:otherwise><fmt:message key="header.title"/></c:otherwise>
  </c:choose></title>
  
  <!-- Meta -->
  <meta http-equiv="content-type" content="application/xhtml+xml;charset=utf-8"/>
  <meta http-equiv="Content-Style-Type" content="text/css" />
  <meta name="Copyright" content="&copy;2014 Infinite Automation Systems Inc."/>
  <meta name="DESCRIPTION" content="Mango Automation from Infinite Automation Systems"/>
  <meta name="KEYWORDS" content="Mango Automation from Infinite Automation Systems"/>
  
  <c:if test="${empty dojoURI}">
	<c:set var="dojoURI">/resources/</c:set>
  </c:if>
  
  <!-- Style -->
  <link rel="icon" href="<%= Common.applicationFavicon %>"/>
  <link rel="shortcut icon" href="<%= Common.applicationFavicon %>"/>

  <link rel="stylesheet" type="text/css" href="${dojoURI}dojox/editor/plugins/resources/css/StatusBar.css"/>  
  <link rel="stylesheet" type="text/css" href="${dojoURI}dojox/layout/resources/FloatingPane.css"/> 
  <link rel="stylesheet" type="text/css" href="${dojoURI}dijit/themes/${theme}/${theme}.css"/> 
  <link rel="stylesheet" type="text/css" href="${dojoURI}dojo/resources/dojo.css"/>
  <link rel="stylesheet" type="text/css" href="/resources/common.css"/>
  
  <c:forEach items="<%= Common.moduleStyles %>" var="modStyle">
    <link rel="stylesheet" type="text/css" href="/${modStyle}"/></c:forEach>
  <c:forEach items="${css}" var="modStyle">
    <link rel="stylesheet" type="text/css" href="${modStyle}"/></c:forEach>
  <jsp:invoke fragment="styles"/>
  
  <!-- Scripts -->
  <script type="text/javascript" src="${dojoURI}dojo/dojo.js" data-dojo-config="has:{'dojo-firebug': true}, async: false, parseOnLoad: true, isDebug:true, extraLocale: ['${lang}']"></script>
  <script type="text/javascript" src="/dwr/engine.js"></script>
  <script type="text/javascript" src="/dwr/util.js"></script>
  <script type="text/javascript" src="/dwr/interface/MiscDwr.js"></script>
  <script type="text/javascript" src="/resources/soundmanager2-nodebug-jsmin.js"></script>
  <script type="text/javascript"  src="/resources/jquery/jquery-1.11.2.js"></script>
  <script type="text/javascript"  src="/resources/notify-combined.min.js"></script>
  <script type="text/javascript" src="/resources/common.js"></script>

  <c:forEach items="${dwr}" var="dwrname">
    <script type="text/javascript" src="/dwr/interface/${dwrname}.js"></script></c:forEach>
  <c:forEach items="${js}" var="jspath">
    <script type="text/javascript" src="${jspath}"></script></c:forEach>
  <script type="text/javascript">
    mango.i18n = <sst:convert obj="${clientSideMessages}"/>;
  </script>
  <c:if test="${!simple}">
    <script type="text/javascript" src="/resources/header.js"></script>
    <script type="text/javascript">
      dwr.util.setEscapeHtml(false);
      <c:if test="${!empty sessionUser}">
        dojo.ready(mango.header.onLoad);
        dojo.ready(function() { setUserMuted(${sessionUser.muted}); });
      </c:if>
      
      function setLocale(locale) {
          MiscDwr.setLocale(locale, function() { window.location = window.location });
      }
      
      function goHomeUrl() {
          MiscDwr.getHomeUrl(function(loc) { window.location = loc; });
      }
      
      function setHomeUrl() {
          MiscDwr.setHomeUrl(window.location.href, function() { alert("<fmt:message key="header.homeUrlSaved"/>"); });
      }
      
      function deleteHomeUrl() {
          MiscDwr.deleteHomeUrl(function() { alert("<fmt:message key="header.homeUrlDeleted"/>"); });
      }
    </script>
  </c:if>
  <c:forEach items="<%= Common.moduleScripts %>" var="modScript">
    <script type="text/javascript" src="/${modScript}"></script></c:forEach>
  <c:choose>
    <c:when test="${!empty siteAnalyticsHead}">${siteAnalyticsHead}</c:when>
  </c:choose>
</head>

<body class="${theme}">

<!-- i18n Messaging Layer, there is also a servlet that does this -->
<jsp:include page="/WEB-INF/snippet/message.jsp"/>


<div id="mainContainer" style="height:100%">
    <div id="headerArea">
      <page:header showHeader="${showHeader}"/>
      <page:toolbar showToolbar="${showToolbar}"/>
      <jsp:include page="/WEB-INF/snippet/errorBox.jsp"/>
    </div>
    <div id="mainContent" style="padding:5px; ">
        <jsp:doBody/>
    </div>
    <c:if test="${empty showFooter || showFooter == true}">
    <div id="footerArea" style="float:left; width:100%; height:60px;">
      <table width="100%" cellspacing="0" cellpadding="0" border="0">
        <tr><td colspan="2">&nbsp;</td></tr>
        <tr>
          <td colspan="2" class="footer" align="center">&copy;2014 Infinite Automation Systems Inc. <fmt:message key="footer.rightsReserved"/></td>
        </tr>
        <tr>
          <td colspan="2" align="center"><a href="http://www.infiniteautomation.com/" ><b></b>www.InfiniteAutomation.com</a></td>
        </tr>
      </table>    
    </div>
    </c:if>
</div>


<c:if test="${!empty onload}">
  <script type="text/javascript">
     dojo.ready(${onload});
  </script>
</c:if>

<c:forEach items="<%= Common.moduleJspfs %>" var="modJspf">
  <jsp:include page="${modJspf}" /></c:forEach>

<!-- Include the Export Dialog -->
<jsp:include page="/WEB-INF/snippet/exportDialog.jsp"/>

<c:choose>
  <c:when test="${!empty siteAnalyticsBody}">${siteAnalyticsBody}</c:when>
</c:choose>
</body>
</html>
