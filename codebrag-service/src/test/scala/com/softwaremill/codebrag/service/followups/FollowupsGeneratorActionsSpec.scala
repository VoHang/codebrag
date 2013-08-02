package com.softwaremill.codebrag.service.followups

import org.scalatest.matchers.ShouldMatchers
import org.scalatest.{BeforeAndAfterEach, FlatSpec}
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.bson.types.ObjectId
import com.softwaremill.codebrag.dao._
import org.mockito.ArgumentCaptor
import com.softwaremill.codebrag.domain._
import org.joda.time.DateTime
import org.mockito.BDDMockito._
import com.softwaremill.codebrag.domain.reactions.{UnlikeEvent, LikeEvent}
import scala.Some
import com.softwaremill.codebrag.domain.Like
import com.softwaremill.codebrag.domain.reactions.LikeEvent
import com.softwaremill.codebrag.domain.Followup
import scala.Some
import com.softwaremill.codebrag.domain.reactions.UnlikeEvent
import com.softwaremill.codebrag.domain.Like
import com.softwaremill.codebrag.domain.builder.{CommentAssembler, LikeAssembler}

class FollowupsGeneratorActionsSpec extends FlatSpec with ShouldMatchers with BeforeAndAfterEach with MockitoSugar {

  behavior of "FollowupsGeneratorActions"

  var generator: FollowupsGeneratorActions = _
  var followupDaoMock: FollowupDAO = _
  var userDaoMock: UserDAO = _
  var commitDaoMock: CommitInfoDAO = _
  var followupWithReactionsDaoMock: FollowupWithReactionsDAO = _

  val commitId = new ObjectId
  val likeId = new ObjectId
  val likeSenderId = new ObjectId
  val likeDate = new DateTime
  val likeAuthorName = "Like Author Name"
  val commitAuthorName = "Lazy Val"
  val likeFileName = "file.txt"
  val likeLineNumber = 27
  val like = Like(likeId, commitId, likeSenderId, likeDate, Some(likeFileName), Some(likeLineNumber))
  val event = LikeEvent(like)

  override def beforeEach() {
    followupDaoMock = mock[FollowupDAO]
    userDaoMock = mock[UserDAO]
    commitDaoMock = mock[CommitInfoDAO]
    followupWithReactionsDaoMock = mock[FollowupWithReactionsDAO]

    generator = new FollowupsGeneratorActions {
      override def followupDao = followupDaoMock
      override def userDao = userDaoMock
      override def commitDao = commitDaoMock
      override def followupWithReactionsDao = followupWithReactionsDaoMock
    }
  }

  it should "generate a followup for author of liked commit" in {
    // given
    val likeAuthor = mock[User]
    val commitAuthor = mock[User]
    given(userDaoMock.findById(likeSenderId)).willReturn(Some(likeAuthor))
    given(likeAuthor.name).willReturn(likeAuthorName)
    val commitMock = mock[CommitInfo]
    given(commitDaoMock.findByCommitId(commitId)).willReturn(Some(commitMock))
    given(commitMock.authorName).willReturn(commitAuthorName)
    given(userDaoMock.findByUserName(commitAuthorName)).willReturn(Some(commitAuthor))

    // when
    generator.handleCommitLiked(event)

    // then
    val followupArgument = ArgumentCaptor.forClass(classOf[Followup])

    verify(followupDaoMock).createOrUpdateExisting(followupArgument.capture())
    val resultFollowup: Followup = followupArgument.getValue
    resultFollowup.reaction.id should equal(likeId)
    resultFollowup.reaction.postingTime should equal(likeDate)
    resultFollowup.reaction.commitId should equal(commitId)
    resultFollowup.reaction.fileName.get should equal(likeFileName)
    resultFollowup.reaction.lineNumber.get should equal(likeLineNumber)
  }

  it should "not generate a follow-up if commit author doesn't exist" in {
    // given
    val userMock = mock[User]
    given(userDaoMock.findById(likeSenderId)).willReturn(Some(userMock))
    given(userMock.name).willReturn(likeAuthorName)
    val commitMock = mock[CommitInfo]
    given(commitDaoMock.findByCommitId(commitId)).willReturn(Some(commitMock))
    given(commitMock.authorName).willReturn(commitAuthorName)
    given(userDaoMock.findByUserName(commitAuthorName)).willReturn(None)

    // when
    generator.handleCommitLiked(event)

    // then
    verifyZeroInteractions(followupDaoMock)
  }

  it should "remove followup for given thread if loaded followup has no reactions already (like was already removed)" in {
    // given
    val likeToUnlike = LikeAssembler.likeFor(commitId).get
    val followup = FollowupWithNoReactions(new ObjectId, new ObjectId, ThreadDetails(commitId))
    given(followupWithReactionsDaoMock.findAllContainingReaction(likeToUnlike.id)).willReturn(List(Left(followup)))

    // when
    generator.handleUnlikeEvent(UnlikeEvent(likeToUnlike.id))

    // then
    verify(followupDaoMock).delete(followup.followupId)
  }

  it should "remove followup for given thread if removed like was the only reaction" in {
    // given
    val likeToUnlike = LikeAssembler.likeFor(commitId).get
    val followup = FollowupWithReactions(new ObjectId, new ObjectId, ThreadDetails(commitId), likeToUnlike, List(likeToUnlike))
    given(followupWithReactionsDaoMock.findAllContainingReaction(likeToUnlike.id)).willReturn(List(Right(followup)))

    // when
    generator.handleUnlikeEvent(UnlikeEvent(likeToUnlike.id))

    // then
    verify(followupDaoMock).delete(followup.followupId)
  }

  it should "update followup for given thread if removed like was not the only reaction" in {
    // given
    val likeToUnlike = LikeAssembler.likeFor(commitId).get
    val comment = CommentAssembler.commentFor(commitId).get
    val followup = FollowupWithReactions(new ObjectId, new ObjectId, ThreadDetails(commitId), likeToUnlike, List(likeToUnlike, comment))
    given(followupWithReactionsDaoMock.findAllContainingReaction(likeToUnlike.id)).willReturn(List(Right(followup)))

    // when
    generator.handleUnlikeEvent(UnlikeEvent(likeToUnlike.id))

    // then
    val captor = ArgumentCaptor.forClass(classOf[FollowupWithReactions])
    verify(followupWithReactionsDaoMock).update(captor.capture())
    val modifiedFollowup = captor.getValue
    modifiedFollowup.allReactions should be(List(comment))
    modifiedFollowup.lastReaction should be(comment)
    modifiedFollowup.followupId should be(followup.followupId)
  }

}
