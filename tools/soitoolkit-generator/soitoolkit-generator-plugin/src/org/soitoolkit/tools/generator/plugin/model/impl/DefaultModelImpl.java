package org.soitoolkit.tools.generator.plugin.model.impl;

import static org.soitoolkit.tools.generator.plugin.model.impl.ModelUtil.capitalize;
import static org.soitoolkit.tools.generator.plugin.model.impl.ModelUtil.makeJavaName;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.soitoolkit.tools.generator.plugin.model.IModel;
import org.soitoolkit.tools.generator.plugin.model.ServiceDescriptorModel;
import org.soitoolkit.tools.generator.plugin.model.enums.MuleVersionEnum;
import org.soitoolkit.tools.generator.plugin.model.enums.TransportEnum;
import org.soitoolkit.tools.generator.plugin.util.PreferencesUtil;
import org.soitoolkit.tools.generator.plugin.util.XmlUtil;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

public class DefaultModelImpl implements IModel {

	private static final String SOITOOLKIT_VERSION = "0.1.7";

	private String groupId;
	private String artifactId;
	private String version;

	private String service;

	private MuleVersionEnum     muleVersion;
	private List<TransportEnum> transports;
	
	private ServiceDescriptorModel serviceDescriptorModel;

	private Map<String, Object> extentions = new HashMap<String, Object>();
	
	private GroovyShell groovyShell;
	
	/**
	 * @param groupId
	 * @param artifactId
	 * @param version
	 * @param service
	 * @param transports
	 * @param serviceDescriptor
	 * @param operations
	 */
	public void initModel(String groupId, String artifactId, String version, String service, MuleVersionEnum muleVersion, List<TransportEnum> transports, String serviceDescriptor, List<String> operations) {

		this.groupId = groupId;
		this.artifactId = artifactId;
		this.version = version;
		this.service = service;
		this.muleVersion = muleVersion;
		this.transports = transports;

		serviceDescriptorModel = (serviceDescriptor == null) ? null : new ServiceDescriptorModel(this, serviceDescriptor, operations);

		groovy.lang.Binding binding = new Binding();
		binding.setVariable("m", this);
    	groovyShell = new GroovyShell(getClass().getClassLoader(), binding);
	}
	
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#resolveParameter(java.lang.String)
	 */
	public Object resolveParameter(String parameterName) {
		return resolveParameter(parameterName, parameterName);
	}
	
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#resolveParameter(java.lang.String, java.lang.Object)
	 */
	public Object resolveParameter(String parameterName, Object defaultValue) {
		try {
			return groovyShell.evaluate("m." + parameterName);
		} catch (RuntimeException rt) {
			// TODO: Debug warning here...
			return defaultValue;
		}
	}

	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getSd()
	 */
	public ServiceDescriptorModel getSd() {
		return serviceDescriptorModel;
	}
	
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getExt()
	 */
	public Map<String, Object> getExt() {
		return extentions;
	}
	
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getDollarSymbol()
	 */
	public String getDollarSymbol() {
		return "$";
	}

	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getXmlTimestamp()
	 */
	public String getXmlTimestamp() {
		return XmlUtil.convertDateToXmlDate(new Date()).toString();
	}

	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getGroupId()
	 */
	public String getGroupId() {
		return groupId;
	}
	
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getArtifactId()
	 */
	public String getArtifactId() {
		return artifactId;
	}
		
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getCapitalizedArtifactId()
	 */
	public String getCapitalizedArtifactId() {
		return capitalize(getArtifactId());
	}
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getLowercaseArtifactId()
	 */
	public String getLowercaseArtifactId() {
		return getArtifactId().toLowerCase();
	}
	
	public String getCapitalizedJavaArtifactId() {
		return capitalize(getJavaArtifactId());
	}
	
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getVersion()
	 */
	public String getVersion() {
		return version;
	}

	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getService()
	 */
	public String getService() {
		return service;
	}
	
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getCapitalizedService()
	 */
	public String getCapitalizedService() {
		return capitalize(getService());
	}
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getLowercaseService()
	 */
	public String getLowercaseService() {
		return getService().toLowerCase();
	}
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getUppercaseService()
	 */
	public String getUppercaseService() {
		return getService().toUpperCase();
	}
	
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getSoitoolkitVersion()
	 */
	public String getSoitoolkitVersion() {
		return SOITOOLKIT_VERSION;
	}

	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getSuperpomGroupId()
	 */
	public String getSuperpomGroupId() {
		return "org.soitoolkit.commons.poms";
	}

	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getSuperpomArtifactId()
	 */
	public String getSuperpomArtifactId() {
		return "soitoolkit-default-parent";
	}
	
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getSuperpomVersion()
	 */
	public String getSuperpomVersion() {
		return getSoitoolkitVersion();
	}
	

	
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getParentPom()
	 */
	public String getParentPom() {
		return getArtifactId() + "-parent";
	}
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getIntegrationComponentProject()
	 */
	public String getIntegrationComponentProject() {
		return getArtifactId();
	}
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getServiceProject()
	 */
	public String getServiceProject() {
		return getArtifactId() + "-services";
	}
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getServiceProjectFilePath()
	 */
	@Override
	public String getServiceProjectFilepath() {
		return getServiceProject();
	}

	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getSchemaProject()
	 */
	public String getSchemaProject() {
		return getArtifactId() + "-schemas";
	}
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getSchemaProject()
	 */
	public String getSchemaProjectFilepath() {
		return getSchemaProject();
	}
	
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getWebProject()
	 */
	public String getWebProject() {
		return getArtifactId() + "-web";
	}
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getWebProjectFilepath()
	 */
	@Override
	public String getWebProjectFilepath() {
		return getWebProject();
	}
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getTeststubWebProject()
	 */
	public String getTeststubWebProject() {
		return getArtifactId() + "-teststub-web";
	}
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getTeststubWebProjectFilepath()
	 */
	@Override
	public String getTeststubWebProjectFilepath() {
		return getTeststubWebProject();
	}

	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getJavaPackage()
	 */
	public String getJavaPackage() {
		String javaPackage = getJavaGroupId() + "." + getJavaArtifactId(); 
		return javaPackage.toLowerCase();
	}
	/* (non-Javadoc)
	 * @see org.soitoolkit.tools.generator.plugin.model.IModel#getJavaPackageFilepath()
	 */
	public String getJavaPackageFilepath() {
		return getJavaPackage().replace('.', '/');
	}

    public String getDefaultSftpIdentityFile() {
    	return PreferencesUtil.getDefaultSftpIdentityFile();
    }
    public String getDefaultSftpIdentityPassphrase() {
    	return PreferencesUtil.getDefaultSftpIdentityPassphrase();
    }

    // JMS Naming...

    public String getJmsInQueue() {
    	return getJavaArtifactId().toUpperCase() + "." + getService().toUpperCase() + ".IN.QUEUE"; 
    }
    public String getJmsOutQueue() {
    	return getJavaArtifactId().toUpperCase() + "." + getService().toUpperCase() + ".OUT.QUEUE"; 
    }
    public String getJmsDLQueue() {
    	return "DLQ." + getJmsInQueue(); 
    }
    public String getJmsLogInfoQueue() {
    	return "SOITOOLKIT.LOG.INFO"; 
    }
    public String getJmsLogErrorQueue() {
    	return "SOITOOLKIT.LOG.ERROR"; 
    }

    // Mule version
    public String getMuleVersion() {
    	return muleVersion.getPomSuffix();
    }

    // Transports
	@Override
	public boolean isSftp() {
		return isTransportSelected(TransportEnum.SFTP);
	}

	@Override
	public boolean isJms() {
		return isTransportSelected(TransportEnum.JMS);
	}	

	@Override
	public boolean isServlet() {
		return isTransportSelected(TransportEnum.SERVLET);
	}	


    // Property files
    public String getConfigPropertyFile() {
    	return getArtifactId() + "-config";
    }
	public String getSecurityPropertyFile() {
    	return getArtifactId() + "-security";
    }


	// --------------------	

	private boolean isTransportSelected(TransportEnum selectedTransport) {

		if (transports == null) return false;

		for (TransportEnum transport : transports) {
			if (transport == selectedTransport) return true;
		}
		return false;
	}

	private String getJavaGroupId() {
		return makeJavaName(getGroupId());
	}
	
	private String getJavaArtifactId() {
		return makeJavaName(getArtifactId());
	}

}
