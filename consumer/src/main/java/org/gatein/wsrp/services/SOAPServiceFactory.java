/*
* JBoss, a division of Red Hat
* Copyright 2008, Red Hat Middleware, LLC, and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/

package org.gatein.wsrp.services;

import org.gatein.common.util.ParameterValidation;
import org.oasis.wsrp.v1.WSRPV1MarkupPortType;
import org.oasis.wsrp.v1.WSRPV1PortletManagementPortType;
import org.oasis.wsrp.v1.WSRPV1RegistrationPortType;
import org.oasis.wsrp.v1.WSRPV1ServiceDescriptionPortType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Service;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="mailto:chris.laprun@jboss.com">Chris Laprun</a>
 * @version $Revision$
 */
public class SOAPServiceFactory implements ManageableServiceFactory
{
   private final Logger log = LoggerFactory.getLogger(getClass());

   private String wsdlDefinitionURL;

   private final static QName SERVICE = new QName("urn:oasis:names:tc:wsrp:v1:wsdl", "WSRPService");
   private final static QName WSRPServiceDescriptionService = new QName("urn:oasis:names:tc:wsrp:v1:wsdl", "WSRPServiceDescriptionService");
   private final static QName WSRPBaseService = new QName("urn:oasis:names:tc:wsrp:v1:wsdl", "WSRPBaseService");
   private final static QName WSRPPortletManagementService = new QName("urn:oasis:names:tc:wsrp:v1:wsdl", "WSRPPortletManagementService");
   private final static QName WSRPRegistrationService = new QName("urn:oasis:names:tc:wsrp:v1:wsdl", "WSRPRegistrationService");

   private Map<Class, Object> services = new ConcurrentHashMap<Class, Object>();
   private String markupURL;
   private String serviceDescriptionURL;
   private String portletManagementURL;
   private String registrationURL;
   private boolean failed;
   private boolean available;

   public <T> T getService(Class<T> clazz) throws Exception
   {
      log.debug("Getting service for class " + clazz);

      // if we need a refresh, reload information from WSDL
      if (!isAvailable() && !isFailed())
      {
         start();
      }

      Object service = services.get(clazz);

      //
      String portAddress = null;
      boolean isMandatoryInterface = false;
      if (WSRPV1ServiceDescriptionPortType.class.isAssignableFrom(clazz))
      {
         portAddress = serviceDescriptionURL;
         isMandatoryInterface = true;
      }
      else if (WSRPV1MarkupPortType.class.isAssignableFrom(clazz))
      {
         portAddress = markupURL;
         isMandatoryInterface = true;
      }
      else if (WSRPV1RegistrationPortType.class.isAssignableFrom(clazz))
      {
         portAddress = registrationURL;
      }
      else if (WSRPV1PortletManagementPortType.class.isAssignableFrom(clazz))
      {
         portAddress = portletManagementURL;
      }

      // Get the stub from the service, remember that the stub itself is not threadsafe
      // and must be customized for every request to this method.
      if (service != null)
      {
         if (portAddress != null)
         {
            log.debug("Setting the end point to: " + portAddress);
            ((BindingProvider)service).getRequestContext().put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, portAddress);

            T result = ServiceWrapper.getServiceWrapper(clazz, service, this);

            // if we managed to retrieve a service, we're probably available
            setFailed(false);
            setAvailable(true);

            return result;
         }
         else
         {
            if (isMandatoryInterface)
            {
               setFailed(true);
               throw new IllegalStateException("Mandatory interface URLs were not properly initialized: no proper service URL for "
                  + clazz.getName());
            }
            else
            {
               throw new IllegalStateException("No URL was provided for optional interface " + clazz.getName());
            }
         }
      }
      else
      {
         return null;
      }
   }

   public boolean isAvailable()
   {
      return available;
   }

   public boolean isFailed()
   {
      return failed;
   }

   public String getServiceDescriptionURL()
   {
      return serviceDescriptionURL;
   }

   public String getMarkupURL()
   {
      return markupURL;
   }

   public String getRegistrationURL()
   {
      return registrationURL;
   }

   public String getPortletManagementURL()
   {
      return portletManagementURL;
   }

   public void setServiceDescriptionURL(String serviceDescriptionURL)
   {
      ParameterValidation.throwIllegalArgExceptionIfNullOrEmpty(serviceDescriptionURL, "Mandatory Service Description interface", null);
      this.serviceDescriptionURL = serviceDescriptionURL;
      setFailed(false); // reset failed status to false since we can't assert it anymore
   }

   public void setMarkupURL(String markupURL)
   {
      ParameterValidation.throwIllegalArgExceptionIfNullOrEmpty(markupURL, "Mandatory Markup interface", null);
      this.markupURL = markupURL;
      setFailed(false); // reset failed status to false since we can't assert it anymore
   }

   public void setRegistrationURL(String registrationURL)
   {
      this.registrationURL = registrationURL;
      setFailed(false); // reset failed status to false since we can't assert it anymore
   }

   public void setPortletManagementURL(String portletManagementURL)
   {
      this.portletManagementURL = portletManagementURL;
      setFailed(false); // reset failed status to false since we can't assert it anymore
   }


   public void stop()
   {
      // todo: implement as needed
   }

   public void setFailed(boolean failed)
   {
      this.failed = failed;
   }

   public void setAvailable(boolean available)
   {
      this.available = available;
   }

   public String getWsdlDefinitionURL()
   {
      return wsdlDefinitionURL;
   }

   public void setWsdlDefinitionURL(String wsdlDefinitionURL) throws Exception
   {
      if (wsdlDefinitionURL == null || wsdlDefinitionURL.length() == 0)
      {
         throw new IllegalArgumentException("Require a non-empty, non-null URL specifying where to find the WSRP " +
            "services definition");
      }

      // only modify WSDL URL if it's different from the previous one as this will trigger re-import of data from remote host
      if (!wsdlDefinitionURL.equals(this.wsdlDefinitionURL))
      {
         this.wsdlDefinitionURL = wsdlDefinitionURL;

         // we need a refresh so mark as not available but not failed
         setAvailable(false);
         setFailed(false);
      }
   }

   public void start() throws Exception
   {
      try
      {
         URL wsdlURL = new URL(wsdlDefinitionURL);

         Service service = Service.create(wsdlURL, SERVICE);

//         WSRPV1MarkupPortType markupPortType = service.getPort(WSRPBaseService, WSRPV1MarkupPortType.class);
         WSRPV1MarkupPortType markupPortType = service.getPort(WSRPV1MarkupPortType.class);
         services.put(WSRPV1MarkupPortType.class, markupPortType);
         markupURL = (String)((BindingProvider)markupPortType).getRequestContext().get(BindingProvider.ENDPOINT_ADDRESS_PROPERTY);

//         WSRPV1ServiceDescriptionPortType sdPort = service.getPort(WSRPServiceDescriptionService, WSRPV1ServiceDescriptionPortType.class);
         WSRPV1ServiceDescriptionPortType sdPort = service.getPort(WSRPV1ServiceDescriptionPortType.class);
         services.put(WSRPV1ServiceDescriptionPortType.class, sdPort);
         serviceDescriptionURL = (String)((BindingProvider)sdPort).getRequestContext().get(BindingProvider.ENDPOINT_ADDRESS_PROPERTY);

//         WSRPV1PortletManagementPortType managementPortType = service.getPort(WSRPPortletManagementService, WSRPV1PortletManagementPortType.class);
         WSRPV1PortletManagementPortType managementPortType = service.getPort(WSRPV1PortletManagementPortType.class);
         services.put(WSRPV1PortletManagementPortType.class, managementPortType);
         portletManagementURL = (String)((BindingProvider)managementPortType).getRequestContext().get(BindingProvider.ENDPOINT_ADDRESS_PROPERTY);

//         WSRPV1RegistrationPortType registrationPortType = service.getPort(WSRPRegistrationService, WSRPV1RegistrationPortType.class);
         WSRPV1RegistrationPortType registrationPortType = service.getPort(WSRPV1RegistrationPortType.class);
         services.put(WSRPV1RegistrationPortType.class, registrationPortType);
         registrationURL = (String)((BindingProvider)registrationPortType).getRequestContext().get(BindingProvider.ENDPOINT_ADDRESS_PROPERTY);

         setFailed(false);
         setAvailable(true);
      }
      catch (MalformedURLException e)
      {
         setFailed(true);
         throw new IllegalArgumentException(wsdlDefinitionURL + " is not a well-formed URL specifying where to find the WSRP services definition.", e);
      }
      catch (Exception e)
      {
         log.info("Couldn't access WSDL information. Service won't be available", e);
         setAvailable(false);
         setFailed(true);
         throw e;
      }
   }
}