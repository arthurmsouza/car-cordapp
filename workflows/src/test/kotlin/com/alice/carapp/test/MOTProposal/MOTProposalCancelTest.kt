package com.alice.carapp.test.MOTProposal

import com.alice.carapp.contracts.MOTProposalContract
import com.alice.carapp.flows.MOTProposal.*
import com.alice.carapp.helper.Vehicle
import com.alice.carapp.states.MOTProposal
import com.alice.carapp.states.StatusEnum
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.money.FiatCurrency
import com.r3.corda.lib.tokens.money.GBP
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow

import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MOTProposalCancelTest {
    private lateinit var mockNetwork: MockNetwork
    private lateinit var a: StartedMockNode
    private lateinit var b: StartedMockNode
    private lateinit var vehicle: Vehicle

    @Before
    fun setup() {
        mockNetwork = MockNetwork(listOf("com.alice.carapp"),
                notarySpecs = listOf(MockNetworkNotarySpec(CordaX500Name("Notary","London","GB"))))
        a = mockNetwork.createPartyNode()
        b = mockNetwork.createPartyNode()
        vehicle = Vehicle(123, "registrationABC", "SG", "model", "cate", 123123)

        // For real nodes this happens automatically, but we have to manually register the flow for tests.
        listOf(a, b).forEach { it.registerInitiatedFlow(MOTProposalIssueFlowResponder::class.java) }

        mockNetwork.runNetwork()
    }

    @After
    fun tearDown() {
        mockNetwork.stopNodes()
    }

    fun issueProposal(ap: StartedMockNode): SignedTransaction {
        val proposal = MOTProposal(a.info.legalIdentities.first(), b.info.legalIdentities.first(), vehicle, 100.GBP, StatusEnum.DRAFT, ap.info.legalIdentities.first())
        val flow = MOTProposalIssueFlow(proposal)
        val future = ap.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun distributeMOTProposal(linearId: UniqueIdentifier, newPrice: Amount<TokenType>, ap: StartedMockNode): SignedTransaction {
        val flow = MOTProposalDistributeFlow(linearId, newPrice)
        val future = ap.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun agreeMOTProposal(linearId: UniqueIdentifier, ap: StartedMockNode): SignedTransaction {
        val flow = MOTProposalAgreeFlow(linearId)
        val future = ap.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    fun rejectMOTProposal(linearId: UniqueIdentifier, ap: StartedMockNode): SignedTransaction {
        val flow = MOTProposalRejectFlow(linearId)
        val future = ap.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()

    }

    fun cancelMOTProposal(linearId: UniqueIdentifier, ap: StartedMockNode): SignedTransaction {
        val flow = MOTProposalCancelFlow(linearId)
        val future = ap.startFlow(flow)
        mockNetwork.runNetwork()
        return future.getOrThrow()
    }

    // Cancel Flow test
    /*
    1. input status
    3. health check (I/O number & type, status)
     */


    /*
        the wrong input status
        a issue MOTProposal
        a distribute MOTProposal
     */
    @Test
    fun testInputStatus() {
        val issueTx = issueProposal(a)
        val linearId = (issueTx.tx.outputs.single().data as MOTProposal).linearId
        val distributeTx = distributeMOTProposal(linearId, 100.GBP, a)
        distributeMOTProposal(linearId, 150.GBP, b)
        assertFailsWith<TransactionVerificationException> {cancelMOTProposal(linearId, a)}
        agreeMOTProposal(linearId, a)
        cancelMOTProposal(linearId, a)
    }

    // health check
    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val issueTx = issueProposal(a)
        val linearId = (issueTx.tx.outputs.single().data as MOTProposal).linearId
        val distributeTx = distributeMOTProposal(linearId, 100.GBP, a)
        val distributeTx2 = distributeMOTProposal(linearId, 150.GBP, b)
        val agreeTx = agreeMOTProposal(linearId, a)
        val cancelTx = cancelMOTProposal(linearId, a)
        assert(cancelTx.tx.inputs.size == 1)
        assert(cancelTx.tx.outputs.isEmpty())
        assert(cancelTx.tx.inputs.single() == StateRef(agreeTx.id, 0))
        val command = cancelTx.tx.commands.single()
        assert(command.value is MOTProposalContract.Commands.Cancel)
        cancelTx.verifyRequiredSignatures()
        println("Signed transaction hash: ${cancelTx.id}")
        listOf(a, b).map {
            it.services.validatedTransactions.getTransaction(cancelTx.id)
        }.forEach {
            val txHash = (it as SignedTransaction).id
            println("$txHash == ${cancelTx.id}")
            assertEquals(cancelTx.id, txHash)
        }
    }

    /*
    agree
    cancel
    issue duplicate
     */
//    fun testForFun() {
//        val issueTx = issueProposal(a)
//        val linearId = (issueTx.tx.outputs.single().data as MOTProposal).linearId
//        val distributeTx = distributeMOTProposal(linearId, 100.GBP, a)
//        val distributeTx2 = distributeMOTProposal(linearId, 150.GBP, b)
//        val agreeTx = agreeMOTProposal(linearId, a)
//        val proposal = agreeTx.tx.outputs.single().data as MOTProposal
//        val cancelTx = cancelMOTProposal(linearId, a)
//
//        val flow = MOTProposalIssueFlow(proposal.copy(status = StatusEnum.DRAFT))
//        val future = a.startFlow(flow)
//        mockNetwork.runNetwork()
//        assertFails { future.getOrThrow() }
//    }


}