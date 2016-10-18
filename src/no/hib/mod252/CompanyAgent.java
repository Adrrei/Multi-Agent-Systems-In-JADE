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
 * The more responders there are, the higher the probability is for the payment to decrease.
 * 
 * Arguments (Required): "Job Title (String), Payment (Integer)"
 * The first argument is the title of the job, while the second argument is the starting payment for the job.
 */
public class CompanyAgent extends Agent {
	private static final long serialVersionUID = 1L;
	private Hashtable<String, Integer> availableJobs;
	private ArrayList<Integer> paymentList;
	private DFHelper helper;
	private String jobTitle = null;
	private String payment = null;
	private int initialPayment;

	/**
	 * Registers the agent with the Directory Facilitator as a Company, 
	 * and prepares the agent for an outgoing message.
	 */
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

				paymentList = new ArrayList<Integer>();
				paymentList.add(initialPayment);

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
					init.addReceiver(new AID((String) agent.getLocalName(), AID.ISLOCALNAME));
				}
				System.out.println();

				if (agents.length == 0) {
					System.out.println("No agents matching the type were found. Terminating: " + getAgent().getAID().getName());
					helper.killAgent(getAgent());
				} else {
					init.setProtocol(FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET);
					init.setReplyByDate(new Date(System.currentTimeMillis() + 10000));
					init.setContent(jobTitle + "|" + payment);

					messages.addElement(init);
				}

				return messages;
			}

			protected void handlePropose(ACLMessage propose, Vector v) {
				System.out.println(propose.getSender().getName() + " proposes $" + propose.getContent() + " for the job: \"" + jobTitle + "\".");
			}

			protected void handleRefuse(ACLMessage refuse) {
				globalResponses++;
				System.out.println(refuse.getSender().getName() + " is not willing to bid any lower.");
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
			 * Handles the responses from other responders, and decides whether to send a new CFP (if multiple responders are remaining), 
			 * or accept the proposal from a responder (if the responder is the only one left in the auction).
			 */
			protected void handleAllResponses(Vector responses, Vector acceptances) {
				int agentsLeft = responses.size() - globalResponses;
				globalResponses = 0;

				System.out.println("\n" + getAID().getName() + " is handling all: Received " + agentsLeft + " responses.");

				int bestProposal = Integer.parseInt(payment);
				ACLMessage reply = new ACLMessage(ACLMessage.CFP);
				Vector<ACLMessage> cfpVector = new Vector<ACLMessage>();
				Enumeration<?> e = responses.elements();
				ArrayList<ACLMessage> responderList = new ArrayList<ACLMessage>();

				while (e.hasMoreElements()) {
					ACLMessage msg = (ACLMessage) e.nextElement();
					if (msg.getPerformative() == ACLMessage.PROPOSE) {
						int proposal = Integer.parseInt(msg.getContent());
						reply = msg.createReply();
						reply.setPerformative(ACLMessage.CFP);
						responderList.add(reply);
						if (proposal <= bestProposal) {
							bestProposal = proposal;
						}
						cfpVector.addElement(reply);
					}
				}
				if (agentsLeft > 1) {
					paymentList.add(bestProposal);

					for (int i = 0; i < responderList.size(); i++) {
						responderList.get(i).setContent(jobTitle + "|" + bestProposal);
						cfpVector.set(i, responderList.get(i));
					}
					
					System.out.println(agentsLeft + " carriers are still bidding: Proceeding to the next round.");
					System.out.println(getAID().getName() + " is issuing CFP's with a payment of $" + paymentList.get(paymentList.size() - 1) + ".\n");
					newIteration(cfpVector);
				} else if (agentsLeft == 1) {
					reply.setPerformative(ACLMessage.REJECT_PROPOSAL);
					if (bestProposal <= paymentList.get(paymentList.size() - 1)) {
						reply.setContent(jobTitle + "|" + bestProposal);
						reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
					}
					acceptances.addElement(reply);
				} else {
					System.out.println("No agent accepted the job.");
				}
			}

		});
	}

	/**
	 * Adds a new job to a hashtable.
	 * @param jobTitle - the title of the job
	 * @param payment - the payment for the job
	 */
	public void updateJobListings(final String jobTitle, final int payment) {
		addBehaviour(new OneShotBehaviour() {
			private static final long serialVersionUID = 1L;

			public void action() {
				availableJobs.put(jobTitle, new Integer(payment));
				System.out.println(getAID().getName() + " has issued a new job: \"" + jobTitle + "\", starting at $" + payment + ".\n");
			}
		});
	}
}
