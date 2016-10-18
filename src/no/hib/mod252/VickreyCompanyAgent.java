package no.hib.mod252;

import no.hib.mod252.DFHelper;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.proto.ContractNetInitiator;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.ServiceDescription;

/**
 * This class creates an agent who acts as an initiator.
 * Its role is to handle incoming bids for an item (in this case, a job) the agent created.
 * The agent wants the lowest possible value, such that the agent pays the least amount possible in order to get a job done.
 * In a Vickrey auction, there's only one round, hence no iterated contract net protocol.
 * 
 * Arguments (Required): "Job Title (String), Payment (Integer)"
 * The first argument is the title of the job, while the second argument is the starting payment for the job.
 */
public class VickreyCompanyAgent extends Agent {
	private static final long serialVersionUID = 1L;
	private Hashtable<String, Integer> availableJobs;
	private DFHelper helper;
	private String jobTitle = null;
	private String payment = null;
	private int initialPayment;

	/**
	 * Registers the agent with the Directory Facilitator as a Company, 
	 * and prepares the agent for an outgoing message.
	 */
	@Override
	protected void setup() {
		helper = DFHelper.getInstance();
		availableJobs = new Hashtable<String, Integer>();

		Object[] args = getArguments();
		if (args.length == 2) {
			jobTitle = (String) args[0];
			payment = (String) args[1];

			if (payment.matches("^\\d+$")) {
				initialPayment = Double.valueOf(payment).intValue();

				updateJobListings(jobTitle, initialPayment);

				ServiceDescription serviceDescription = new ServiceDescription();
				serviceDescription.setType("Company");
				serviceDescription.setName(getLocalName());
				helper.register(this, serviceDescription);
			} else {
				System.out.println("Payment must be a positive number (e.g. 100).");
				System.out.println("Terminating: " + this.getAID().getName());
				doDelete();
			}
		} else {
			System.out.println("Two arguments required. Please provide arguments in the format \"Job Title, Payment\", where Payment is a number (e.g. 100).");
			System.out.println("Terminating: " + this.getAID().getName());
			doDelete();
		}

		addBehaviour(new ContractNetInitiator(this, null) {
			private static final long serialVersionUID = 1L;
			private int globalResponses = 0;

			/**
			 * Is initiated on startup, and sends a CFP message to agents listed as the type "Carrier".
			 * The message contains the title for the job as well as its payment.
			 */
			public Vector<ACLMessage> prepareCfps(ACLMessage init) {
				init = new ACLMessage(ACLMessage.CFP);
				Vector<ACLMessage> messages = new Vector<ACLMessage>();

				AID[] agents = helper.searchDF(getAgent(), "Carrier");

				System.out.println("The Directory Facilitator found the following agents labeled as \"Carrier\": ");
				for (AID agent : agents) {
					System.out.println(agent.getName());
					init.addReceiver(new AID(agent.getLocalName(), AID.ISLOCALNAME));
				}
				System.out.println();

				if (agents.length == 0) {
					System.out.println("No agents matching the type were found. Terminating: " + getAgent().getAID().getName());
					helper.killAgent(getAgent());
				} else {
					init.setProtocol(FIPANames.InteractionProtocol.FIPA_CONTRACT_NET);
					init.setReplyByDate(new Date(System.currentTimeMillis() + 10000));
					init.setContent(jobTitle + "|" + payment);

					messages.addElement(init);
				}

				return messages;
			}

			protected void handlePropose(ACLMessage propose, Vector v) {
				System.out.println(propose.getSender().getName() + " proposed $" + propose.getContent() + " for the job: \"" + jobTitle + "\".");
			}

			
			protected void handleRefuse(ACLMessage refuse) {
				globalResponses++;
				System.out.println(refuse.getSender().getName() + " is not bidding for the job.");
				helper.removeReceiverAgent(refuse.getSender(), refuse);
			}

			
			protected void handleFailure(ACLMessage failure) {
				globalResponses++;
				System.out.println(failure.getSender().getName() + " failed to reply.");
				helper.removeReceiverAgent(failure.getSender(), failure);
			}

			/**
			 * Once a responder responds with INFORM, the initiator knows that the job
			 * has been accepted, so all the agents whom took part in the auction can terminate.
			 */
			protected void handleInform(ACLMessage inform) {
				globalResponses++;
				System.out.println("\n" + getAID().getName() + " has no further jobs available.");
				availableJobs.remove(jobTitle);
				for (Agent agent : helper.getRegisteredAgents()) {
					helper.killAgent(agent);
				}
			}

			/**
			 * Handles the responses from other responders, and sends a REJECT_PROPOSAL to all the agents, 
			 * except the agent with the lowest bid, which will receive an ACCEPT_PROPOSAL.
			 */
			protected void handleAllResponses(Vector responses, Vector acceptances) {
				int agentsLeft = responses.size() - globalResponses;
				globalResponses = 0;

				System.out.println("\n" + getAID().getName() + " is handling all: Received " + agentsLeft + " responses.");

				ACLMessage reply = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
				Enumeration<?> e = responses.elements();
				ArrayList<Integer> proposals = new ArrayList<Integer>();
				ArrayList<AID> responders = new ArrayList<AID>();

				while (e.hasMoreElements()) {
					ACLMessage msg = (ACLMessage) e.nextElement();
					if (msg.getPerformative() == ACLMessage.PROPOSE) {
						responders.add(msg.getSender());
						proposals.add(Integer.parseInt(msg.getContent()));
						reply = msg.createReply();
						reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
						acceptances.addElement(reply);
					}
				}
				
				if (responders.isEmpty() || proposals.isEmpty()) {
					System.out.println("No agent accepted the job.");
				} else {
					int bestProposal = proposals.get(0);
					AID bestProposer = responders.get(0);
					reply = (ACLMessage) acceptances.get(0);
					for (int i = 1; i < proposals.size(); i++) {
						if (proposals.get(i) < bestProposal) {
							bestProposal = proposals.get(i);
							bestProposer = responders.get(i);
							reply = (ACLMessage) acceptances.get(i);
						}
					}
					
					for (AID responder : responders) {
						if (responder.equals(bestProposer)) {
							reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
							reply.setContent(jobTitle + "|" + bestProposal);
						}
					}
				}
			}

		});
	}

	/**
	 * Adds a new job to a hashtable.
	 * 
	 * @param jobTitle - the title of the job
	 * @param payment - the payment for the job
	 */
	public void updateJobListings(final String jobTitle, final int payment) {
		addBehaviour(new OneShotBehaviour() {
			private static final long serialVersionUID = 1L;

			@Override
			public void action() {
				availableJobs.put(jobTitle, new Integer(payment));
				System.out.println(getAID().getName() + " has issued a new job: \"" + jobTitle + "\", starting at $" + payment + ".\n");
			}
		});
	}
}
