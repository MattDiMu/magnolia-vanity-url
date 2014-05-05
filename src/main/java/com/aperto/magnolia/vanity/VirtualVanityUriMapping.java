package com.aperto.magnolia.vanity;

import info.magnolia.cms.beans.config.QueryAwareVirtualURIMapping;
import info.magnolia.cms.core.AggregationState;
import info.magnolia.context.MgnlContext;
import info.magnolia.module.templatingkit.ExtendedAggregationState;
import info.magnolia.module.templatingkit.sites.Site;
import info.magnolia.templating.functions.TemplatingFunctions;
import org.apache.jackrabbit.value.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.util.Map;
import java.util.regex.PatternSyntaxException;

import static com.aperto.magnolia.vanity.app.LinkConverter.isExternalLink;
import static info.magnolia.cms.util.RequestDispatchUtil.REDIRECT_PREFIX;
import static info.magnolia.jcr.util.PropertyUtil.getString;
import static info.magnolia.repository.RepositoryConstants.WEBSITE;
import static javax.jcr.query.Query.JCR_SQL2;
import static org.apache.commons.lang.StringUtils.*;

/**
 * Virtual Uri Mapping of vanity URLs managed in the vanity url app.
 *
 * @author frank.sommer
 */
public class VirtualVanityUriMapping implements QueryAwareVirtualURIMapping {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualVanityUriMapping.class);
    private static final String QUERY = "select * from [mgnl:vanityUrl] where vanityUrl = $vanityUrl and site = $site";

    private TemplatingFunctions _templatingFunctions;
    private VanityUrlModule _vanityUrlModule;

    @Inject
    public void setTemplatingFunctions(TemplatingFunctions templatingFunctions) {
        _templatingFunctions = templatingFunctions;
    }

    @Inject
    public void setVanityUrlModule(VanityUrlModule vanityUrlModule) {
        _vanityUrlModule = vanityUrlModule;
    }

    // CHECKSTYLE:OFF
    @Override
    public MappingResult mapURI(String uri) {
        // CHECKSTYLE:ON
        return mapURI(uri, null);
    }

    // CHECKSTYLE:OFF
    @Override
    public MappingResult mapURI(String uri, String queryString) {
        // CHECKSTYLE:ON
        MappingResult result = null;
        try {
            if (isVanityCandidate(uri)) {
                String toUri = getUriOfVanityUrl(uri);
                if (isNotBlank(toUri)) {
                    if (isNotBlank(queryString)) {
                        toUri = toUri.concat("?" + queryString);
                    }
                    result = new MappingResult();
                    result.setToURI(toUri);
                    result.setLevel(uri.length());
                }
            }
        } catch (PatternSyntaxException e) {
            LOGGER.error("A vanity url exclude pattern is not set correctly.", e);
        }
        return result;
    }

    private boolean isVanityCandidate(String uri) {
        boolean contentUri = true;
        Map<String, String> excludes = _vanityUrlModule.getExcludes();
        if (excludes != null) {
            for (String exclude : excludes.values()) {
                if (isNotEmpty(uri) && isNotEmpty(exclude) && uri.matches(exclude)) {
                    contentUri = false;
                    break;
                }
            }
        }
        return contentUri;
    }

    protected String getUriOfVanityUrl(String vanityUrl) {
        String redirectUri = EMPTY;
        try {
            Session jcrSession = MgnlContext.getJCRSession("vanityUrls");
            QueryManager queryManager = jcrSession.getWorkspace().getQueryManager();
            Query query = queryManager.createQuery(QUERY, JCR_SQL2);
            query.bindValue("vanityUrl", new StringValue(vanityUrl));
            query.bindValue("site", new StringValue(retrieveSite()));
            QueryResult queryResult = query.execute();
            NodeIterator nodes = queryResult.getNodes();
            if (nodes.hasNext()) {
                Node node = nodes.nextNode();
                String link = getString(node, "link");
                String url = link;
                if (!isExternalLink(link)) {
                    String contextPath = MgnlContext.getWebContext().getRequest().getContextPath();
                    url = removeStart(_templatingFunctions.link(WEBSITE, link), contextPath);
                }
                redirectUri = REDIRECT_PREFIX + url;
            }
        } catch (RepositoryException e) {
            LOGGER.warn("Error on querying for vanity url.", e);
        }
        return redirectUri;
    }

    private String retrieveSite() {
        String siteName = "default";

        AggregationState aggregationState = MgnlContext.getAggregationState();
        if (aggregationState instanceof ExtendedAggregationState) {
            Site site = ((ExtendedAggregationState) aggregationState).getSite();
            siteName = site.getName();
        }

        return siteName;
    }
}
