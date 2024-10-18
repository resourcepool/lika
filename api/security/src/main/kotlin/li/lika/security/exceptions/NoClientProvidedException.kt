package li.lika.security.exceptions

import li.lika.security.core.Headers

class NoClientProvidedException: MessageAndErrorCodeSecurityException("security.missing.client.header","No client header provided. Please provide a valid ${Headers.AUTH_HEADER_NAME} header")
