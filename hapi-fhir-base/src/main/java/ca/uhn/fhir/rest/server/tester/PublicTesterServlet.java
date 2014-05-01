package ca.uhn.fhir.rest.server.tester;

/*
 * #%L
 * HAPI FHIR Library
 * %%
 * Copyright (C) 2014 University Health Network
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateProcessingParameters;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.resourceresolver.IResourceResolver;
import org.thymeleaf.standard.StandardDialect;
import org.thymeleaf.templateresolver.TemplateResolver;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.RuntimeResourceDefinition;
import ca.uhn.fhir.model.api.IQueryParameterType;
import ca.uhn.fhir.model.dstu.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu.resource.Conformance;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.rest.annotation.Metadata;
import ca.uhn.fhir.rest.client.GenericClient;
import ca.uhn.fhir.rest.client.api.IBasicClient;
import ca.uhn.fhir.rest.server.Constants;
import ca.uhn.fhir.rest.server.EncodingUtil;

public class PublicTesterServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(PublicTesterServlet.class);
	private static final boolean DEBUGMODE = true;
	private TemplateEngine myTemplateEngine;
	private HashMap<String, String> myStaticResources;
	private String myServerBase;
	private FhirContext myCtx;

	public void setServerBase(String theServerBase) {
		myServerBase = theServerBase;
	}

	public PublicTesterServlet() {
		myStaticResources = new HashMap<String, String>();
		myStaticResources.put("jquery-2.1.0.min.js", "text/javascript");
		myStaticResources.put("PublicTester.js", "text/javascript");
		myStaticResources.put("PublicTester.css", "text/css");
		myStaticResources.put("hapi_fhir_banner.png", "image/png");
		myStaticResources.put("hapi_fhir_banner_right.png", "image/png");
		myStaticResources.put("shCore.js", "text/javascript");
		myStaticResources.put("shBrushJScript.js", "text/javascript");
		myStaticResources.put("shBrushXml.js", "text/javascript");
		myStaticResources.put("shBrushPlain.js", "text/javascript");
		myStaticResources.put("shCore.css", "text/css");
		myStaticResources.put("shThemeDefault.css", "text/css");

		myCtx = new FhirContext();
	}

	@Override
	protected void doPost(HttpServletRequest theReq, HttpServletResponse theResp) throws ServletException, IOException {
		if (DEBUGMODE) {
			myTemplateEngine.getCacheManager().clearAllCaches();
		}

		try {
			GenericClient client = (GenericClient) myCtx.newRestfulGenericClient(myServerBase);
			client.setKeepResponses(true);
			String method = theReq.getParameter("method");

			String requestUrl;
			String action;
			String resultStatus;
			String resultBody;
			String resultSyntaxHighlighterClass;

			if ("read".equals(method)) {
				RuntimeResourceDefinition def = getResourceType(theReq);
				String id = StringUtils.defaultString(theReq.getParameter("id"));
				if (StringUtils.isBlank(id)) {
					theResp.sendError(Constants.STATUS_HTTP_400_BAD_REQUEST, "No ID specified");
				}
				client.read(def.getImplementingClass(), new IdDt(id));

			}
			if ("vread".equals(method)) {
				RuntimeResourceDefinition def = getResourceType(theReq);
				String id = StringUtils.defaultString(theReq.getParameter("id"));
				if (StringUtils.isBlank(id)) {
					theResp.sendError(Constants.STATUS_HTTP_400_BAD_REQUEST, "No ID specified");
				}

				String versionId = StringUtils.defaultString(theReq.getParameter("versionid"));
				if (StringUtils.isBlank(versionId)) {
					theResp.sendError(Constants.STATUS_HTTP_400_BAD_REQUEST, "No Version ID specified");
				}
				client.vread(def.getImplementingClass(), new IdDt(id), new IdDt(versionId));

			} else if ("searchType".equals(method)) {

				Map<String, List<IQueryParameterType>> params = new HashMap<String, List<IQueryParameterType>>();

				HashSet<String> hashSet = new HashSet<String>(theReq.getParameterMap().keySet());
				String paramName = null;
				IQueryParameterType paramValue = null;
				while (hashSet.isEmpty() == false) {
					
					String nextKey = hashSet.iterator().next();
					String nextValue = theReq.getParameter(nextKey);
					paramName=null;
					paramValue=null;
					
					if (nextKey.startsWith("param.token.")) {
						int prefixLength = "param.token.".length();
						paramName = nextKey.substring(prefixLength + 2);
						String systemKey = "param.token."+"1." + paramName;
						String valueKey = "param.token."+"2." + paramName;
						String system = theReq.getParameter(systemKey);
						String value = theReq.getParameter(valueKey);
						paramValue = new IdentifierDt(system, value);
						hashSet.remove(systemKey);
						hashSet.remove(valueKey);
					} else if (nextKey.startsWith("param.string.")) {
						paramName = nextKey.substring("param.string.".length());
						paramValue = new StringDt(nextValue);
					}

					if (paramName != null) {
						if (params.containsKey(paramName) == false) {
							params.put(paramName, new ArrayList<IQueryParameterType>());
						}
						params.get(paramName).add(paramValue);
					}

					hashSet.remove(nextKey);
				}
				
				RuntimeResourceDefinition def = getResourceType(theReq);
				client.search(def.getImplementingClass(), params);

			} else {
				theResp.sendError(Constants.STATUS_HTTP_400_BAD_REQUEST, "Invalid method: " + method);
				return;
			}

			HttpRequestBase lastRequest = client.getLastRequest();
			requestUrl = lastRequest.getURI().toASCIIString();
			action = client.getLastRequest().getMethod();
			resultStatus = client.getLastResponse().getStatusLine().toString();
			resultBody = client.getLastResponseBody();

			ContentType ct = ContentType.get(client.getLastResponse().getEntity());
			String mimeType = ct.getMimeType();
			EncodingUtil ctEnum = EncodingUtil.forContentType(mimeType);
			switch (ctEnum) {
			case JSON:
				resultSyntaxHighlighterClass = "brush: jscript";
				break;
			case XML:
				resultSyntaxHighlighterClass = "brush: xml";
				break;
			default:
				resultSyntaxHighlighterClass = "brush: plain";
				break;
			}

			WebContext ctx = new WebContext(theReq, theResp, theReq.getServletContext(), theReq.getLocale());
			ctx.setVariable("base", myServerBase);
			ctx.setVariable("requestUrl", requestUrl);
			ctx.setVariable("action", action);
			ctx.setVariable("resultStatus", resultStatus);
			ctx.setVariable("resultBody", StringEscapeUtils.escapeHtml4(resultBody));
			ctx.setVariable("resultSyntaxHighlighterClass", resultSyntaxHighlighterClass);

			myTemplateEngine.process(PUBLIC_TESTER_RESULT_HTML, ctx, theResp.getWriter());
		} catch (Exception e) {
			ourLog.error("Failure during processing", e);
			theResp.sendError(500, e.toString());
		}
	}

	private RuntimeResourceDefinition getResourceType(HttpServletRequest theReq) throws ServletException {
		String resourceName = StringUtils.defaultString(theReq.getParameter("resourceName"));
		RuntimeResourceDefinition def = myCtx.getResourceDefinition(resourceName);
		if (def == null) {
			throw new ServletException("Invalid resourceName: " + resourceName);
		}
		return def;
	}

	@Override
	protected void doGet(HttpServletRequest theReq, HttpServletResponse theResp) throws ServletException, IOException {
		if (DEBUGMODE) {
			myTemplateEngine.getCacheManager().clearAllCaches();
		}

		ourLog.info("RequestURI: {}", theReq.getPathInfo());

		String resName = theReq.getPathInfo().substring(1);
		if (myStaticResources.containsKey(resName)) {
			streamResponse(resName, myStaticResources.get(resName), theResp);
			return;
		}

		ConformanceClient client = myCtx.newRestfulClient(ConformanceClient.class, myServerBase);
		Conformance conformance = client.getConformance();

		WebContext ctx = new WebContext(theReq, theResp, theReq.getServletContext(), theReq.getLocale());
		ctx.setVariable("conf", conformance);
		ctx.setVariable("base", myServerBase);
		ctx.setVariable("jsonEncodedConf", myCtx.newJsonParser().encodeResourceToString(conformance));
		myTemplateEngine.process(theReq.getPathInfo(), ctx, theResp.getWriter());

	}

	private interface ConformanceClient extends IBasicClient {
		@Metadata
		Conformance getConformance();
	}

	@Override
	public void init(ServletConfig theConfig) throws ServletException {
		myTemplateEngine = new TemplateEngine();
		TemplateResolver resolver = new TemplateResolver();
		resolver.setResourceResolver(new ProfileResourceResolver());
		myTemplateEngine.setTemplateResolver(resolver);
		StandardDialect dialect = new StandardDialect();
		myTemplateEngine.setDialect(dialect);
		myTemplateEngine.initialize();
	}

	private static final String PUBLIC_TESTER_RESULT_HTML = "/PublicTesterResult.html";

	private final class ProfileResourceResolver implements IResourceResolver {

		@Override
		public String getName() {
			return getClass().getCanonicalName();
		}

		@Override
		public InputStream getResourceAsStream(TemplateProcessingParameters theTemplateProcessingParameters, String theName) {
			ourLog.debug("Loading template: {}", theName);
			if ("/".equals(theName)) {
				return PublicTesterServlet.class.getResourceAsStream("/ca/uhn/fhir/rest/server/tester/PublicTester.html");
			}
			if (PUBLIC_TESTER_RESULT_HTML.equals(theName)) {
				return PublicTesterServlet.class.getResourceAsStream("/ca/uhn/fhir/rest/server/tester/PublicTesterResult.html");
			}

			return null;
		}
	}

	private void streamResponse(String theResourceName, String theContentType, HttpServletResponse theResp) throws IOException {
		InputStream res = PublicTesterServlet.class.getResourceAsStream("/ca/uhn/fhir/rest/server/tester/" + theResourceName);
		theResp.setContentType(theContentType);
		IOUtils.copy(res, theResp.getOutputStream());
	}

}
