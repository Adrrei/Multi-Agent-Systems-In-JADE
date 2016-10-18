package no.hib.mod252;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

/**
 * This class creates an agent who acts as a responder. Its role is to negotiate
 * with another Employee agent in order to get a better deal than a set default deal.
 */
public class EmployeeAgent extends Agent {
	private static final long serialVersionUID = 1L;
	private DFHelper helper;

	/**
	 * Registers the agent with the Directory Facilitator as an Employee, 
	 * and prepares the agent for an incoming message.
	 */
	protected void setup() {
		helper = DFHelper.getInstance();
		ServiceDescription serviceDescription = new ServiceDescription();
		serviceDescription.setName(getLocalName());
		serviceDescription.setType("Employee");

		helper.register(this, serviceDescription);

		addBehaviour(new CyclicBehaviour(this) {
			private static final long serialVersionUID = 1L;

			public void action() {
				ACLMessage cfp = receive(MessageTemplate.MatchPerformative(ACLMessage.CFP));
				ACLMessage accept = receive(MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL));

				if (cfp != null) {
					handleCfp(cfp);
				} else if (accept != null) {
					handleAcceptProposal(accept);
				} else {
					block();
				}
			}
		});
	}

	/**
	 * Responds to the CFP message from another agent with a new ACCEPT_PROPOSAL message.
	 * @param cfp - the incoming message to handle
	 */
	protected void handleCfp(ACLMessage cfp) {
		String employeeName = cfp.getContent().substring(cfp.getContent().lastIndexOf("|") + 1);
		String content = cfp.getContent().substring(0, cfp.getContent().lastIndexOf("|") - 2);
		System.out.println(employeeName + " is proposing " + content + " for " + getAID().getName() + ".\n");
		ACLMessage accept = cfp.createReply();
		accept.setContent(cfp.getContent());
		accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
		send(accept);
	}

	/**
	 * The agent receives an ACCEPT_PROPOSAL message, so a new delegation is in order.
	 * @param accept - the incoming message to handle
	 */
	protected void handleAcceptProposal(ACLMessage accept) {
		String content = accept.getContent();
		String senderName = accept.getSender().getLocalName();
		String localName = getLocalName().substring(getLocalName().lastIndexOf(":") + 1);
		String employeeName = senderName.substring(senderName.lastIndexOf(":") + 1);
		int cost = Integer.parseInt(content.substring(content.lastIndexOf("|") - 1, content.lastIndexOf("|"))) - 1;
		System.out.println(accept.getSender().getName() + " accepts the proposal!");
		System.out.print("New total cost: " + cost + ", with delegation: ");
		if (accept.getContent().contains("2 for 2")) {
			System.out.println(localName + "(a, c), " + employeeName + "(b, d)");
		} else {
			System.out.println(employeeName + "(a, d, c), " + localName + "(b)");
		}
		ACLMessage inform = accept.createReply();
		inform.setPerformative(ACLMessage.INFORM);
		send(inform);
	}

	protected void handleInform(ACLMessage inform) {
		System.out.println("The employees negotiation was successful!");
	}
}
