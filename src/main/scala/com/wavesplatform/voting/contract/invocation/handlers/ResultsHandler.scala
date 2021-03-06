package com.wavesplatform.voting.contract.invocation.handlers

import com.wavesplatform.protobuf.common.DataEntry
import com.wavesplatform.protobuf.service.{ContractKeysResponse, ContractTransaction}
import com.wavesplatform.voting.contract.VotingError
import com.wavesplatform.voting.contract.invocation.{CallHandler, VotingStateService}
import com.wavesplatform.voting.contract.util.ContractKeysResponseExtension._
import com.wavesplatform.voting.contract.util.DataEntriesExtension._
import com.wavesplatform.voting.contract.validators.VotingValidators

import scala.collection.Seq
import scala.concurrent.Future

/**
  * Проверки:
  *
  * Проверить что отправитель указан в ключе SERVER_<publicKey>, его тип SERVER_<publicKey>.type равен main и SERVER_<publicKey>.status равен active
  * Проверить что обязательные поля не пустые
  * Проверить что VOTING_BASE.status равно completed
  * Проверить что для всех активных серверов значение SERVER_<publicKey>.dkgRound  одинаковое и равно VOTING_BASE.round (все на актуальном раунде)
  * Проверить что для всех активных серверов SERVER_<publicKey>.dkgComplaints[] пустое (нет жалоб)
  *
  * Запись:
  *
  * Сохранить значение results в ключ RESULTS стейта контракта
  */
object ResultsHandler extends CallHandler {

  override def getContractState(
    contractTransaction: ContractTransaction,
    stateService: VotingStateService): Future[ContractKeysResponse] = {
    stateService.requestWithServers(contractTransaction, Seq("VOTING_BASE", "SERVERS"))
  }

  def call(
    contractTransaction: ContractTransaction,
    contractState: ContractKeysResponse): Either[VotingError, Seq[DataEntry]] = {
    val txParams = contractTransaction.params

    for {
      servers    <- contractState.getServers
      _          <- VotingValidators.mainServerCanVote(contractTransaction, servers)
      votingBase <- VotingValidators.validateVotingCompleted(contractState)
      _          <- VotingValidators.checkActiveServersRound(votingBase.dkgRound, servers)
      _          <- VotingValidators.checkActiveServersComplaintsEmptiness(servers)
      results    <- txParams.extractStringParam("results")
    } yield Seq(DataEntry("RESULTS", DataEntry.Value.StringValue(results)))
  }
}
