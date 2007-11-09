#include <string>
#include <map>

#include "ogrsh/DynamicallyLoadedSymbol.hpp"
#include "ogrsh/Logging.hpp"
#include "ogrsh/Provider.hpp"
#include "ogrsh/Session.hpp"
#include "ogrsh/XMLUtilities.hpp"

#include "xercesc/dom/DOMElement.hpp"

#include "providers/geniifs/GeniiFSProvider.hpp"
#include "providers/geniifs/GeniiFSSession.hpp"

namespace ogrsh
{
	namespace geniifs
	{
		static const char* _ROOT_RNS_URL_ATTR_NAME = "root-rns-url";

		ogrsh::Session* GeniiFSProvider::createSession(
			const std::string &sessionName,
			const xercesc_2_8::DOMElement &configElement)
		{
			std::string rootRNSUrl = ogrsh::getAttribute(
				(xercesc_2_8::DOMElement*)(&configElement),
				_ROOT_RNS_URL_ATTR_NAME);

			if (rootRNSUrl.length() == 0)
			{
				OGRSH_FATAL("Configuration for session \""
					<< sessionName
					<< "\" is invalid.  Missing required attribute \""
					<< _ROOT_RNS_URL_ATTR_NAME << "\".");
				ogrsh::shims::real_exit(1);
			}

			OGRSH_TRACE("Creating a GeniiFSSession with sessionName = "
				<< sessionName
				<< ", and root RNS URL = " << rootRNSUrl << ".");
			return new GeniiFSSession(sessionName, rootRNSUrl);
		}

		GeniiFSProvider::GeniiFSProvider(const std::string &providerName)
			: ogrsh::Provider(providerName)
		{
		}

		GeniiFSProvider::~GeniiFSProvider()
		{
		}

		extern "C" {
			ogrsh::Provider* createGenesisIIOGRSHProvider(
				const std::string &providerName)
			{
				return new GeniiFSProvider(providerName);
			}
		}
	}
}
