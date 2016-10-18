package no.hib.mod252;

import java.util.Random;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.ParallelBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.domain.FIPANames;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.proto.SSIteratedContractNetResponder;
import jade.proto.SSResponderDispatcher;
import jade.wrapper.AgentController;

/**
 * This class creates an agent who acts as a responder.
 * Its role is to bid for an item (in this case, a job). 
 * The agent tries to get the highest possible value for doing a job, 
 * but when there are other responders, they'll underbid each other, in hope for other responders to forfeit.
 * 
 * If the agent wins the auction, it will delegate the job to two of its employees.
 * The employees will then negotiate between each other to get a better deal than the default one.
 * 
 * Arguments (Optional): "Percentage (Integer)" The argument defines how low the
 * agent is willing to go based on the initial payment. If no argument is
 * specified (or the format is invalid) it will use its default value: 50.
 */
public class CarrierNegotiationAgent extends Agent {
	private static final long serialVersionUID = 1L;
	private DFHelper helper;
	private int initialPayment = 0;
	private int percentage = 50;

	/**
	 * Registers the agent with the Directory Facilitator as a Carrier, 
	 * and prepares the agent for an incoming message.
	 */
	protected void setup() {
		helper = DFHelper.getInstance();
		ServiceDescription serviceDescription = new ServiceDescription();
		serviceDescription.setName(getLocalName());
		serviceDescription.setType("Carrier");

		helper.register(this, serviceDescription);

		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			String percentageArg = (String) args[0];
			if (percentageArg.matches("^\\d+$")) {
				percentage = Integer.parseInt(percentageArg);
			}
		}

		final String IP = FIPANames.InteractionProtocol.FIPA_ITERATED_CONTRACT_NET;
		MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchProtocol(IP),
				MessageTemplate.MatchPerformative(ACLMessage.CFP));

		SequentialBehaviour sequential = new SequentialBehaviour();
		addBehaviour(sequential);
		ParallelBehaviour parallel = new ParallelBehaviour(ParallelBehaviour.WHEN_ALL);
		sequential.addSubBehaviour(parallel);
		parallel.addSubBehaviour(new CustomContractNetResponder(this, template));
	}

	private class CustomContractNetResponder extends SSResponderDispatcher {
		private static final long serialVersionUID = 1L;

		private CustomContractNetResponder(Agent agent, MessageTemplate template) {
			super(agent, template);
		}

		protected Behaviour createResponder(ACLMessage message) {
			return new SSIteratedContractNetResponder(myAgent, message) {
				private static final long serialVersionUID = 1L;

				/**
				 * Responds to the CFP message from the initiator with either a PROPOSE/REFUSE message.
				 * If the payment is too low for the agent, it declines with a REFUSE message, 
				 * otherwise, the agent will respond with a PROPOSE message.
				 */
				protected ACLMessage handleCfp(ACLMessage cfp) {
					int payment = 0;
					int backupPayment = 0;
					try {
						payment = Integer.parseInt(cfp.getContent().substring(cfp.getContent().lastIndexOf("|") + 1));
						backupPayment = payment;
					} catch (Exception e) {
						System.out.println(getAID().getName() + " couldn't read the price.");
					}

					if (initialPayment == 0) {
						initialPayment = payment;
					}

					Random generate = new Random();

					int upperBound;
					int length = String.valueOf(payment).length();

					switch (length) {
					case 1:
						upperBound = 1;
						break;
					case 2:
						upperBound = 5;
						break;
					case 3:
						upperBound = 50;
						break;
					default:
						upperBound = 300;
						break;
					}

					int randomNumber = generate.nextInt(upperBound) + 1;
					int lowerBound = (int) (initialPayment * (percentage / 100.0f));

					if (randomNumber != 1 && (payment - randomNumber) > lowerBound) {
						payment = (payment - randomNumber);
					} else {
						payment = 0;
					}

					ACLMessage response = cfp.createReply();

					if (payment > 0) {
						response.setPerformative(ACLMessage.PROPOSE);
						if (helper.getRespondersRemaining() == 1) {
							response.setContent(String.valueOf(backupPayment));
						} else {
							response.setContent(String.valueOf(payment));
						}
					} else {
						upperBound = generate.nextInt(3000) + 1000;
						doWait(upperBound);

						if (helper.getRespondersRemaining() == 1) {
							response.setPerformative(ACLMessage.PROPOSE);
							response.setContent(String.valueOf(backupPayment));
						} else {
							response.setPerformative(ACLMessage.REFUSE);
						}
					}
					return response;
				}

				/**
				 * The agent received an ACCEPT_PROPOSAL message, so it won the auction.
				 */
				protected ACLMessage handleAcceptProposal(ACLMessage msg, ACLMessage propose, ACLMessage accept) {
					if (msg != null) {
						String jobTitle = null;
						int payment = 0;
						try {
							jobTitle = accept.getContent().substring(0, accept.getContent().indexOf("|"));
							payment = Integer.parseInt(accept.getContent().substring(accept.getContent().lastIndexOf("|") + 1));
						} catch (Exception e) {
						}

						System.out.println(getAID().getName() + " has accepted the job: \"" + jobTitle + "\" from "
								+ accept.getSender().getName() + ", and will receive $" + payment + " for completing it.\n");

						delegateTask(jobTitle, 5, "A1", "A2");
						doWait(5000);

						ACLMessage inform = accept.createReply();
						inform.setPerformative(ACLMessage.INFORM);
						return inform;
					} else {
						ACLMessage failure = accept.createReply();
						failure.setPerformative(ACLMessage.FAILURE);
						return failure;
					}
				}

				protected void handleRejectProposal(ACLMessage msg, ACLMessage propose, ACLMessage reject) {
					System.out.println(reject.getSender().getName() + " cannot offer " + getAID().getName() + " the job due to unexpected results.");
				}
			};
		}

		/**
		 * The carrier agent creates two additional agents whose job is to negotiate between each other.
		 * They'll attempt to get a better deal than the default deal A1(a, d, c), A2(b, d).
		 * The Employee agents then use their class to perform this negotiation.
		 * @param jobTitle - the title of the job
		 * @param cost - the cost of the default deal
		 * @param employeeOne - the name of the first employee
		 * @param employeeTwo - the name of the second employee
		 */
		private void delegateTask(String jobTitle, int cost, String employeeOne, String employeeTwo) {
			System.out.println(getAID().getName() + " wants to delegate the job to two of its employees (" + employeeOne + ", " + employeeTwo + ") ...");
			
			createAgent(employeeOne);
			createAgent(employeeTwo);
			doWait(2000);
			
			System.out.println("\nCurrent total cost: " + cost + ", with delegation: " + employeeOne + "(a, d, c), " + employeeTwo + "(b, d)");

			ACLMessage msg = new ACLMessage(ACLMessage.CFP);
			msg.clearAllReceiver();
			msg.clearAllReplyTo();
			
			AID empOne = new AID(getLocalName() + ":" + employeeOne, AID.ISLOCALNAME);
			AID empTwo = new AID(getLocalName() + ":" + employeeTwo, AID.ISLOCALNAME);

			Random selected = new Random();
			if (selected.nextBoolean()) {
				msg.addReplyTo(empOne);
				msg.addReceiver(empTwo);
				msg.setContent("2 for 2|" + cost + "|" + empOne.getName());
			} else {
				msg.addReplyTo(empTwo);
				msg.addReceiver(empOne);
				msg.setContent("3 for 1|" + cost + "|" + empTwo.getName());
			}
			send(msg);
		}

		/**
		 * Creates an Employee agent
		 * @param agentName - the name of the agent
		 */
		private void createAgent(String agentName) {
			AgentController agentController;
			agentName = getLocalName() + ":" + agentName;
			String className = this.getClass().getPackage().getName().toString() + ".EmployeeAgent";

			try {
				agentController = getContainerController().createNewAgent(agentName, className, null);
				agentController.start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
