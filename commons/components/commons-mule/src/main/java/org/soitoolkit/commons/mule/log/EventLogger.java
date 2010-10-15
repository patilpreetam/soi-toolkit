package org.soitoolkit.commons.mule.log;

import static org.soitoolkit.commons.mule.core.PropertyNames.SOITOOLKIT_CONTRACT_ID;
import static org.soitoolkit.commons.mule.core.PropertyNames.SOITOOLKIT_CORRELATION_ID;
import static org.soitoolkit.commons.mule.core.PropertyNames.SOITOOLKIT_INTEGRATION_SCENARIO;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;

import org.mule.DefaultMuleMessage;
import org.mule.MuleServer;
import org.mule.RequestContext;
import org.mule.api.MuleContext;
import org.mule.api.MuleEventContext;
import org.mule.api.MuleException;
import org.mule.api.MuleMessage;
import org.mule.api.config.MuleConfiguration;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.service.Service;
import org.mule.api.transformer.TransformerException;
import org.mule.module.client.MuleClient;
import org.mule.module.xml.stax.ReversibleXMLStreamReader;
import org.mule.transport.jms.JmsMessageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;
import org.soitoolkit.commons.logentry.schema.v1.LogEntryType;
import org.soitoolkit.commons.logentry.schema.v1.LogEvent;
import org.soitoolkit.commons.logentry.schema.v1.LogLevelType;
import org.soitoolkit.commons.logentry.schema.v1.LogMessageExceptionType;
import org.soitoolkit.commons.logentry.schema.v1.LogMessageType;
import org.soitoolkit.commons.logentry.schema.v1.LogMetadataInfoType;
import org.soitoolkit.commons.logentry.schema.v1.LogRuntimeInfoType;
import org.soitoolkit.commons.logentry.schema.v1.LogRuntimeInfoType.BusinessContextId;
import org.soitoolkit.commons.mule.jaxb.JaxbObjectToXmlTransformer;
import org.soitoolkit.commons.mule.jaxb.JaxbUtil;
import org.soitoolkit.commons.mule.util.XmlUtil;

/**
 * Log events in a standardized way
 * 
 * @author Magnus Larsson
 *
 */
public class EventLogger {
	
	private static final Logger messageLogger = LoggerFactory.getLogger("org.soitoolkit.commons.mule.messageLogger");

	// Creating JaxbUtil objects (i.e. JaxbContext objects)  are costly, so we only keep one instance.
	// According to https://jaxb.dev.java.net/faq/index.html#threadSafety this should be fins since they are thread safe!
	private static final JaxbUtil JAXB_UTIL = new JaxbUtil(LogEvent.class);

	// MuleClient used to dispatch log-messages to log-queues, created on first usage 
	private static MuleClient muleClient = null;

	private static final String MSG_ID = "soi-toolkit.log";
	private static final String LOG_EVENT_INFO = "logEvent-info";
	private static final String LOG_EVENT_ERROR = "logEvent-error";
	private static final String LOG_STRING = MSG_ID + 
		"\n** {}.start ***********************************************************" +
		"\nIntegrationScenarioId={}\nContractId={}\nLogMessage={}\nServiceImpl={}\nHost={} ({})\nComponentId={}\nEndpoint={}\nMessageId={}\nBusinessCorrelationId={}\nPayload={}\nBusinessContextId={}" + 
		"\n** {}.end *************************************************************";

	private static InetAddress HOST = null;
	private static String HOST_NAME = "UNKNOWN";
	private static String HOST_IP = "UNKNOWN";
	private static String PROCESS_ID = "UNKNOWN";

	private String serverId = null; // Can't read this one at class initialization because it is not set at that time. Can also be different for different loggers in the same JVM (e.g. multiple wars in one servlet container with shared classes?))

	// Used to transform payloads that are jaxb-objects into a xml-string
	private JaxbObjectToXmlTransformer jaxb2xml = null;

	{
		try {
			// Let's give it a try, fail silently...
			HOST       = InetAddress.getLocalHost();
			HOST_NAME  = HOST.getCanonicalHostName();
			HOST_IP    = HOST.getHostAddress();
			PROCESS_ID = ManagementFactory.getRuntimeMXBean().getName();
		} catch (Throwable ex) {
		}
	}

	public EventLogger() {
	}

	public EventLogger(JaxbObjectToXmlTransformer jaxb2xml) {
		this.jaxb2xml  = jaxb2xml;
	}

	public void logInfoEvent (
		MuleMessage message,
		String      logMessage,
		Map<String, String> businessContextId) {

		if (messageLogger.isInfoEnabled()) {
			LogEvent logEvent = createLogEntry(LogLevelType.INFO, message, logMessage, businessContextId, message.getPayload(), null);
			
			String xmlString = JAXB_UTIL.marshal(logEvent);
			dispatchInfoEvent(xmlString);
//			System.err.println("## SKIP DISPATCH TO soitoolkit-info-log-endpoint");

			String logMsg = formatLogMessage(LOG_EVENT_INFO, logEvent);
			messageLogger.info(logMsg);
		}
	}

	public void logErrorEvent (
		Throwable   error,
		MuleMessage message) {

		LogEvent logEvent = createLogEntry(LogLevelType.ERROR, message, error.toString(), null, message.getPayload(), error);
		
		String xmlString = JAXB_UTIL.marshal(logEvent);
		dispatchErrorEvent(xmlString);
//		System.err.println("## SKIP DISPATCH TO soitoolkit-error-log-endpoint");

		String logMsg = formatLogMessage(LOG_EVENT_ERROR, logEvent);
		messageLogger.error(logMsg);
	}

	public void logErrorEvent (
		Throwable   error,
		Object      payload) {

		LogEvent logEvent = createLogEntry(LogLevelType.ERROR, null, error.toString(), null, payload, error);

		String xmlString = JAXB_UTIL.marshal(logEvent);
		dispatchErrorEvent(xmlString);
//		System.err.println("## SKIP DISPATCH TO soitoolkit-error-log-endpoint");

		String logMsg = formatLogMessage(LOG_EVENT_ERROR, logEvent);
		messageLogger.error(logMsg);
	}

	//----------------
	
	private void dispatchInfoEvent(String msg) {
		dispatchEvent("vm://soitoolkit-info-log", msg);
//		dispatchEvent("soitoolkit-info-log-endpoint", msg);
	}

	private void dispatchErrorEvent(String msg) {
		dispatchEvent("vm://soitoolkit-error-log", msg);
//		dispatchEvent("soitoolkit-error-log-endpoint", msg);
	}

	private void dispatchEvent(String url, String msg) {
		try {
			// TODO: We use MuleClient since RequestContext.getEventContext() returns null when we don't have a request active, e.g. when sftp-notifications are logged...
			getMuleClient().dispatch(url, msg, null);
		} catch (MuleException e) {
			throw new RuntimeException(e);
		}
	}

	private MuleClient getMuleClient() throws MuleException {
		if (muleClient == null) {
			MuleContext mctx = MuleServer.getMuleContext();
			muleClient = new MuleClient(mctx);
		}
		return muleClient;
	}
	
	@SuppressWarnings("unused")
	private void reqCtx_dispatchEvent(String endpointName, String msg) {
//		Session s = null;
		try {
            MuleMessage logMessage = new DefaultMuleMessage(msg);
            MuleEventContext eventCtx = RequestContext.getEventContext();
            if (eventCtx == null) {
            	System.err.println("EventCtx null, can't log to endpoint: " + endpointName + ", logMsg: " + msg);
            } else {
            	eventCtx.dispatchEvent(logMessage, endpointName);
            }

/*			
			EndpointBuilder eb = MuleServer.getMuleContext().getRegistry().lookupEndpointBuilder(endpointName);
			OutboundEndpoint oe = eb.buildOutboundEndpoint();
			System.err.println("### OutboundEndpoint: " + oe.getClass().getName());
			System.err.println("### JMS-Connector: " + oe.getConnector().getClass().getName());
			JmsConnector jmsConn = (JmsConnector)oe.getConnector();
			Connection c = jmsConn.getConnection();
			s = c.createSession(false, Session.AUTO_ACKNOWLEDGE);
			System.err.println("### JMS-Session created!");
*/			
		} catch (MuleException e) {
			throw new RuntimeException(e);
//		} catch (JMSException e) {
//			throw new RuntimeException(e);
//		} finally {
//			if (s != null) try {s.close();} catch (JMSException e) {}
		}		
	}
	
	private String formatLogMessage(String logEventName, LogEvent logEvent) {
		LogMessageType      messageInfo  = logEvent.getLogEntry().getMessageInfo();
		LogMetadataInfoType metadataInfo = logEvent.getLogEntry().getMetadataInfo();
		LogRuntimeInfoType  runtimeInfo  = logEvent.getLogEntry().getRuntimeInfo();

		String integrationScenarioId = metadataInfo.getIntegrationScenarioId();
		String contractId = metadataInfo.getContractId();
		String logMessage = messageInfo.getMessage();
		String serviceImplementation = metadataInfo.getServiceImplementation();
		String componentId = runtimeInfo.getComponentId();
		String endpoint = metadataInfo.getEndpoint();
		String messageId = runtimeInfo.getMessageId();
		String businessCorrelationId = runtimeInfo.getBusinessCorrelationId();
		String payload = logEvent.getLogEntry().getPayload();
		String businessContextIdString = businessContextIdToString(runtimeInfo.getBusinessContextId());
		
		return MessageFormatter.arrayFormat(LOG_STRING, new String[] {logEventName, integrationScenarioId, contractId, logMessage, serviceImplementation, HOST_NAME, HOST_IP, componentId, endpoint, messageId, businessCorrelationId, payload, businessContextIdString, logEventName});
	}
	
	private String businessContextIdToString(List<BusinessContextId> businessContextIds) {
		
		if (businessContextIds == null) return "";
		
		StringBuffer businessContextIdString = new StringBuffer();
		for (BusinessContextId bci : businessContextIds) {
			businessContextIdString.append("\n-").append(bci.getName()).append(" = ").append(bci.getValue());
		}
		return businessContextIdString.toString();
	}

	private String getServerId() {

		if (serverId != null) return serverId;
		
		MuleContext mCtx = MuleServer.getMuleContext();
		if (mCtx == null) return "UNKNOWN.MULE_CONTEXT"; 

		MuleConfiguration mConf = mCtx.getConfiguration();
		if (mConf == null) return "UNKNOWN.MULE_CONFIGURATION"; 
		
		return serverId = mConf.getId();
	}

	private String getPayloadAsString(Object payload) {
		String content = null;
		if (payload instanceof Object[]) {
			Object[] arr = (Object[]) payload;
			int i = 0;
			for (Object object : arr) {
				String arrContent = "[" + i++ + "]: "
						+ getContentAsString(object);
				if (i == 1) {
					content = arrContent;
				} else {
					content += "\n" + arrContent;
				}
			}

		} else {
			content = getContentAsString(payload);
		}
		return content;
	}

	private String getContentAsString(Object payload) {
		String content = null;

		if (payload == null) {
			return null;

		} else if (payload instanceof byte[]) {
			content = getByteArrayContentAsString(payload);

		} else if (payload instanceof ReversibleXMLStreamReader) {
			content = XmlUtil.convertReversibleXMLStreamReaderToString(
					(ReversibleXMLStreamReader) payload, "UTF-8");

		} else if (payload instanceof Message) {
			content = convertJmsMessageToString(payload, "UTF-8");

		} else if (isJabxObject(payload)) {
			content = getJaxbContentAsString(payload, "UTF-8");

			// } else if (payload instanceof ChunkedInputStream) {
			// contents = message.getPayloadAsString();
			// message.setPayload(contents);

		} else {
			// Using message.getPayloadAsString() consumes InputStreams causing
			// exceptions after the logging...
			// contents = message.getPayloadAsString();
			content = payload.toString();
		}

		return content;
	}

	private String convertJmsMessageToString(Object payload, String outputEncoding) {
		try {
			return JmsMessageUtils.toObject((Message) payload, null,
					outputEncoding).toString();
		} catch (JMSException e) {
			throw new RuntimeException(e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private String getByteArrayContentAsString(Object payload) {
		String content;
		StringBuffer byteArray = new StringBuffer();
		byte[] bytes = (byte[]) payload;
		for (int i = 0; i < bytes.length; i++) {
			byteArray.append((char) bytes[i]);
		}
		content = byteArray.toString();
		return content;
	}

	private boolean isJabxObject(Object payload) {
		return payload.getClass().isAnnotationPresent(XmlType.class);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private String getJaxbContentAsString(Object jaxbObject, String outputEncoding) {
		String content;
		if (jaxb2xml == null) {
			content = "Missing jaxb2xml injection, can't marshal JAXB object of type: "
					+ jaxbObject.getClass().getName();
		} else {

			if (!jaxbObject.getClass()
					.isAnnotationPresent(XmlRootElement.class)) {
				// We are missing element end namespace info, let's create a
				// wrapper xml-root-element
				QName wrapperQName = new QName("class:"
						+ jaxbObject.getClass().getName(),
						getJaxbWrapperElementName(jaxbObject));
				jaxbObject = new JAXBElement(wrapperQName, jaxbObject
						.getClass(), null, jaxbObject);
			}

			try {
				content = (String) jaxb2xml.doTransform(jaxbObject,
						outputEncoding);
			} catch (TransformerException e) {
				e.printStackTrace();
				content = "JAXB object marshalling failed: " + e.getMessage();
			}
		}
		return content;
	}

	private String getJaxbWrapperElementName(Object payload) {
		String name = payload.getClass().getSimpleName();
		String elementName = name.substring(0, 1).toLowerCase()
				+ name.substring(1);
		return elementName;
	}

	private LogEvent createLogEntry(
			LogLevelType logLevel,
			MuleMessage message, 
			String logMessage,
			Map<String, String> businessContextId,
			Object payload,
			Throwable exception) {

		// --------------------------
		//
		// 1. Process input variables
		//
		// --------------------------

		// TODO: Will event-context always be null when an error is reported?
		// If so then its probably better to move this code to the info-logger method.
	    String           serviceImplementation = "";
		String           endpoint    = "";
        MuleEventContext event       = RequestContext.getEventContext();
        if (event != null) {
	        Service          service     = event.getService();
		    EndpointURI      endpointURI = event.getEndpointURI();
		    serviceImplementation = (service == null)? "" : service.getName();
			endpoint    = (endpointURI == null)? "" : endpointURI.toString();
        }
		
		String messageId                  = "";
		String integrationScenarioId        = ""; 
		String contractId                 = ""; 
		String businessCorrelationId              = "";

		if (message != null) {
			messageId           = message.getUniqueId();
			contractId          = message.getStringProperty(SOITOOLKIT_CONTRACT_ID, "");
			businessCorrelationId       = message.getStringProperty(SOITOOLKIT_CORRELATION_ID, "");
			integrationScenarioId = message.getStringProperty(SOITOOLKIT_INTEGRATION_SCENARIO, "");
		}

		String componentId = getServerId();

		// Only extract payload if debug is enabled!
	    String payloadASstring = (messageLogger.isDebugEnabled())? getPayloadAsString(payload) : "";
		

	    // -------------------------
	    //
	    // 2. Create LogEvent object
	    //
	    // -------------------------
		
		// Setup basic runtime information for the log entry
		LogRuntimeInfoType lri = new LogRuntimeInfoType();
		lri.setTimestamp(XmlUtil.convertDateToXmlDate(null));
		lri.setHostName(HOST_NAME);
		lri.setHostIp(HOST_IP);
		lri.setProcessId(PROCESS_ID);
		lri.setThreadId(Thread.currentThread().getName());
		lri.setComponentId(componentId);
		lri.setMessageId(messageId);
		lri.setBusinessCorrelationId(businessCorrelationId); 
		
		// Add any business contexts
		if (businessContextId != null) {
			Set<Entry<String, String>> entries = businessContextId.entrySet();
			for (Entry<String, String> entry : entries) {
				BusinessContextId bxid = new BusinessContextId();
				bxid.setName(entry.getKey());
				bxid.setValue(entry.getValue());
				lri.getBusinessContextId().add(bxid);
			}
		}
		

		// Setup basic metadata information for the log entry
		LogMetadataInfoType lmi = new LogMetadataInfoType();
		lmi.setLoggerName(messageLogger.getName());
		lmi.setIntegrationScenarioId(integrationScenarioId);
		lmi.setContractId(contractId);
		lmi.setServiceImplementation(serviceImplementation);
		lmi.setEndpoint(endpoint);

		
		// Setup basic information of the log message for the log entry
		LogMessageType lm = new LogMessageType();
		lm.setLevel(logLevel);
		lm.setMessage(logMessage);
		
		
		// Setup exception information if present
		if (exception != null) {
			LogMessageExceptionType lme = new LogMessageExceptionType();
			
			lme.setExceptionClass(exception.getClass().getName());
			StackTraceElement[] stArr = exception.getStackTrace();
			List<String> stList = new ArrayList<String>();
			for (int i = 0; i < stArr.length; i++) {
				stList.add(stArr[i].toString());
			}
			lme.getStackTrace().addAll(stList);
			
			lm.setException(lme);
		}


		// Create the log entry object
		LogEntryType logEntry = new LogEntryType();
		logEntry.setMetadataInfo(lmi);
		logEntry.setRuntimeInfo(lri);
		logEntry.setMessageInfo(lm);
		logEntry.setPayload(payloadASstring);

		
		// Create the final log event object
		LogEvent logEvent = new LogEvent();
		logEvent.setLogEntry(logEntry);
		
		
		// We are actually done :-)
		return logEvent;
	}
	
}
