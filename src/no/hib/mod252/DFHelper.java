package no.hib.mod252;

import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.SearchConstraints;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAAgentManagement.DFAgentDescription;

/**
 * This class is created upon initialization of a Carrier Agent (responder), 
 * a Company Agent (initiator) or an Employee Agent. Its purpose is to provide the agents 
 * with information relevant to the bidding process, as well as termination of agents.
 */
public final class DFHelper extends Agent {
	private static final long serialVersionUID = 1L;
	private int respondersRemaining = 0;

	private static DFHelper instance = null;
	private ArrayList<Agent> registeredAgents = new ArrayList<Agent>();

	private DFHelper() {
	}

	public static synchronized DFHelper getInstance() {
		if (instance == null) {
			instance = new DFHelper();
		}
		return instance;
	}

	/**
	 * Register a new agent with given properties
	 * @param agent - an agent
	 * @param serviceDescription - properties for the agent
	 */
	public void register(Agent agent, ServiceDescription serviceDescription) {
		DFAgentDescription dfAgentDescription = new DFAgentDescription();
		dfAgentDescription.setName(getAID());
		dfAgentDescription.addServices(serviceDescription);

		try {
			registeredAgents.add(agent);
			DFService.register(agent, dfAgentDescription);
			System.out.println(agent.getName() + " registered as: " + serviceDescription.getType() + ".");
		} catch (FIPAException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Searches for all the agents with a certain type.
	 * @param agent - an agent
	 * @param service - type to search for
	 * @return - an array of AIDs (if any), or null (if none)
	 */
	public AID[] searchDF(Agent agent, String service) {
		DFAgentDescription dfAgentDescription = new DFAgentDescription();
		ServiceDescription serviceDescription = new ServiceDescription();
		serviceDescription.setType(service);
		dfAgentDescription.addServices(serviceDescription);

		SearchConstraints findAll = new SearchConstraints();
		findAll.setMaxResults(new Long(-1));
		
		try {
			DFAgentDescription[] result = DFService.search(agent, dfAgentDescription, findAll);
			AID[] agents = new AID[result.length];
			for (int i = 0; i < result.length; i++) {
				agents[i] = result[i].getName();
				respondersRemaining++;
			}
			return agents;
		} catch (FIPAException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Removes a receiver from the ongoing auction, but does not terminate it.
	 * @param agent - the agent to remove
	 * @param msg - the message it's associated with
	 */
	public void removeReceiverAgent(AID agent, ACLMessage msg) {
		respondersRemaining--;
		System.out.println(agent.getName() + " was removed from receivers.");
		msg.removeReceiver(agent);
	}

	/**
	 * De-registers and kills the specified agent, but states that it simply "left" (brutal)
	 * @param agent - an agent to kill
	 */
	public void killAgent(Agent agent) {
		try {
			System.out.println(agent.getAID().getName() + " left.");
			DFService.deregister(agent);
			agent.doDelete();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Returns the total amount of responders left in the auction.
	 * @return - ^
	 */
	public int getRespondersRemaining() {
		return respondersRemaining;
	}

	/**
	 * Returns a list of registered agents.
	 * @return - ^
	 */
	public ArrayList<Agent> getRegisteredAgents() {
		return registeredAgents;
	}
}
