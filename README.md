# A Multi Agent Auction System developed in JADE

The project aims to showcase different types of auctions using agents, where each agent have their own behavior.
This behavior is made using random number generators and simple logic, but could very well be extended upon.

The interaction protocols used can be found on [FIPA's homepage](http://www.fipa.org/repository/ips.php3 "FIPA's Homepage"):
* [FIPA Contract Net Interaction Protocol Specification](http://www.fipa.org/specs/fipa00029/SC00029H.html "FIPA Contract Net Interaction Protocol Specification")
* [FIPA Iterated Contract Net Interaction Protocol Specification](http://www.fipa.org/specs/fipa00030/SC00030H.html "FIPA Iterated Contract Net Interaction Protocol Specification")

**The classes are connected as follows:**
* All of the classes use DFHelper
* CarrierAgent and CompanyAgent
* VickreyCarrierAgent and VickreyCompanyAgent
* CarrierNegotiationAgent, CompanyNegotiationAgent, and EmployeeAgent

The CarrierAgent/CompanyAgent and CarrierNegotiationAgent/CompanyNegotiationAgent classes use the FIPA Iterated Contract Net Protocol, and can be closely compared to reverse English auctions.

VickreyCarrierAgent/VickreyCompanyAgent use the FIPA Contract Net Protocol, following the standard for Vickrey auctions.
