package nl.han.ica.mad.s478416.npuzzle.activities.gametypes;

import android.content.Intent;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.multiplayer.Participant;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessageReceivedListener;
import com.google.android.gms.games.multiplayer.realtime.RealTimeMessage;
import com.google.android.gms.games.multiplayer.realtime.Room;
import com.google.android.gms.games.multiplayer.realtime.RoomConfig;
import com.google.android.gms.games.multiplayer.realtime.RoomStatusUpdateListener;
import com.google.android.gms.games.multiplayer.realtime.RoomUpdateListener;
import com.google.android.gms.plus.Plus;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import butterknife.ButterKnife;
import butterknife.InjectView;
import nl.han.ica.mad.s478416.npuzzle.R;
import nl.han.ica.mad.s478416.npuzzle.activities.MainMenuActivity;
import nl.han.ica.mad.s478416.npuzzle.model.Difficulty;
import nl.han.ica.mad.s478416.npuzzle.utils.ByteUtils;
import nl.han.ica.mad.s478416.npuzzle.utils.PuzzleImageUtils;

public abstract class AbstractMultiplayerGameActivity extends AbstractGameActivity implements GoogleApiClient.ConnectionCallbacks,
		GoogleApiClient.OnConnectionFailedListener, RealTimeMessageReceivedListener, RoomUpdateListener, RoomStatusUpdateListener {
	private static String TAG = "AbstractMultiplayerGameActivity";

	private static int RC_SIGN_IN = 9001;
	private static final char READY = 'R';
	private static final char DICE_ROLL = 'A'; // r and d are both occupied ):
	private static final char IMAGE_CHOICE = 'I';
	private static final char DIFFICULTY_CHOICE = 'D';
	private static final char SHUFFLE = 'S';
	private static final char MOVE = 'M';
	private static final char FINISHED = 'F';
	private static final char QUIT = 'Q';

	@InjectView(R.id.connectionStatusLayout) RelativeLayout connectionStatusLayout;
	@InjectView(R.id.connectionStatusText) TextView connectionStatusText;

	protected GoogleApiClient googleApiClient;

	private String roomId;
	private String myId;
	protected Participant me;
	protected Participant opponent;
	protected Participant gameLeader;

	private Integer myDiceRoll;
	private Integer opponentsDiceRoll;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_abstract_multiplayer_game);
		ButterKnife.inject(this);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		googleApiClient = new GoogleApiClient.Builder(this)
				.addConnectionCallbacks(this)
				.addOnConnectionFailedListener(this)
				.addApi(Plus.API).addScope(Plus.SCOPE_PLUS_LOGIN)
				.addApi(Games.API).addScope(Games.SCOPE_GAMES)
				.build();
	}

	@Override
	protected void onStart() {
		if (googleApiClient == null || !googleApiClient.isConnected()) {
			Log.d(TAG, "Connecting to Google API");
			connectionStatusText.setText("Connecting to Google API");
			googleApiClient.connect();
		}

		super.onStart();
	}

	@Override
	protected void onStop() {
		leaveRoom();
		super.onStop();
	}

	public void onConnected(Bundle bundle) {
		startQuickGame();
	}

	public void onConnectionSuspended(int cause) {
		Log.d(TAG, "onConnectionSuspended. Trying to reconnect...");
		googleApiClient.connect();
	}

	public void onConnectionFailed(ConnectionResult result) {
		Log.d("MAD", "Connection to Google Api Client failed" + result.toString());

		if (result.hasResolution()) {
			try {
				result.startResolutionForResult(this, RC_SIGN_IN);
			} catch (IntentSender.SendIntentException e) {
				Log.d("MAD", e.getMessage());
				googleApiClient.connect();
			}
		}
	}

	protected void startQuickGame() {
		final int MIN_OPPONENTS = 1, MAX_OPPONENTS = 1;

		Bundle autoMatchCriteria = RoomConfig.createAutoMatchCriteria(MIN_OPPONENTS, MAX_OPPONENTS, 0);

		RoomConfig.Builder rtmConfigBuilder = RoomConfig.builder(this);
		rtmConfigBuilder.setMessageReceivedListener(this);
		rtmConfigBuilder.setRoomStatusUpdateListener(this);
		rtmConfigBuilder.setAutoMatchCriteria(autoMatchCriteria);

		Games.RealTimeMultiplayer.create(googleApiClient, rtmConfigBuilder.build());
	}


	@Override public void onDisconnectedFromRoom(Room room) {
		roomId = null;
		Log.d(TAG, "onDisconnectedFromRoom");
		//goToMainMenu();
	}

	@Override
	public void onLeftRoom(int i, String s) {
		Log.d(TAG, "onLeftRoom");
		goToMainMenu();
	}

	// room events
	@Override public void onRoomCreated(int i, Room room) 						{ Log.d(TAG, "onRoomCreated"); updateRoom(room); }
	@Override public void onRoomAutoMatching(Room room) 						{ Log.d(TAG, "onRoomAutoMatching"); updateRoom(room); }
	@Override public void onRoomConnecting(Room room) 							{ Log.d(TAG, "onRoomConnecting"); updateRoom(room); }
	// peer events
	@Override public void onPeerLeft(Room room, List<String> strings) 			{ updateRoom(room); }
	@Override public void onPeerDeclined(Room room, List<String> strings) 		{ updateRoom(room); }
	@Override public void onPeerInvitedToRoom(Room room, List<String> strings) 	{ updateRoom(room); }
	@Override public void onPeerJoined(Room room, List<String> strings) 		{ updateRoom(room); }
	@Override public void onPeersConnected(Room room, List<String> strings) 	{ updateRoom(room); }
	@Override public void onPeersDisconnected(Room room, List<String> strings) 	{ updateRoom(room); }
	@Override public void onP2PDisconnected(String participant) {}
	@Override public void onP2PConnected(String participant) {}

	public void updateRoom(Room room) {
		roomId = room.getRoomId();
	}

	@Override
	public void onJoinedRoom(int statusCode, Room room) { updateRoom(room); }

	@Override
	public void onConnectedToRoom(Room room) {
		Log.d(TAG, "onConnectedToRoom");
		this.roomId = room.getRoomId();
		this.myId = room.getParticipantId(Games.Players.getCurrentPlayerId(googleApiClient));
		this.me = room.getParticipants().get(0).getParticipantId() == myId ? room.getParticipants().get(0) : room.getParticipants().get(1);
		this.opponent = room.getParticipants().get(0).getParticipantId() == myId ? room.getParticipants().get(1) : room.getParticipants().get(0);
	}

	@Override
	public void onRoomConnected(int statusCode, Room room) {
		if (statusCode != GamesStatusCodes.STATUS_OK) {
			Log.e(TAG, "*** Error: onRoomConnected, status " + statusCode);
		} else {
			updateRoom(room);
		}

		Log.d(TAG, "onRoomConnected");
		connectionStatusText.setText("Found an opponent!");

		determineGameLeader();
	}

	/* nPuzzle logic*/

	private void determineGameLeader() {
		if (this.myDiceRoll == null) rollDice();
		if (this.opponentsDiceRoll == null) return;

		Log.d(TAG, "DeterminingGameLeader");

		if (myDiceRoll == opponentsDiceRoll) {
			// dice rolls are equal, roll again
			myDiceRoll = null;
			opponentsDiceRoll = null;
			determineGameLeader();
		} else {
			this.gameLeader = (myDiceRoll > opponentsDiceRoll) ? me : opponent;

			if (this.gameLeader == this.me) {
				chooseImage();
				chooseDifficulty();
			}
		}
	}

	private void rollDice(){
		Log.d(TAG, "rollDice");
		myDiceRoll = new Random().nextInt(127);
		sendDiceRoll(myDiceRoll);
	}

	private void onDiceRollReceived(int opponentsDiceRoll){
		Log.d(TAG, "onDiceRollReceived");
		this.opponentsDiceRoll = opponentsDiceRoll;
		determineGameLeader();
	}

	protected void hideConnectionStatusView(){
		connectionStatusLayout.setVisibility(View.GONE);
	}

	private void chooseImage(){
		List<Integer> imageResIds = PuzzleImageUtils.getImgResIds();
		int randomImageIndex = new Random().nextInt(imageResIds.size());

		this.sendImageChoice(randomImageIndex);
		onImageChoiceReceived(imageResIds.get(randomImageIndex));
	}

	private void chooseDifficulty(){
		Difficulty chosen = Difficulty.EASY;
		sendDifficultyChoice(chosen);
		onDifficultyChoiceReceived(chosen);
	}

	@Override
	public void onRealTimeMessageReceived(RealTimeMessage rtm) {
		byte[] data = rtm.getMessageData();

		switch (data[0]) {
			case READY:
				onOpponentReady();
				break;
			case DICE_ROLL:
				int number = (int) data[1];
				onDiceRollReceived(number);
				break;
			case IMAGE_CHOICE:
				int imgIndex = (int) data[1];
				int imgResId = PuzzleImageUtils.getImgResIds().get(imgIndex);
				onImageChoiceReceived(imgResId);
				break;
			case DIFFICULTY_CHOICE:
				byte[] difficultyBytes = Arrays.copyOfRange(data, 1, data.length);
				Difficulty difficulty = Difficulty.valueOf( new String(difficultyBytes) );
				onDifficultyChoiceReceived(difficulty);
				break;
			case SHUFFLE:
				byte[] shuffleSequenceBytes = Arrays.copyOfRange(data, 1, data.length);
				int[] shuffleSequence = new int[shuffleSequenceBytes.length];
				for (int i = 0; i < shuffleSequenceBytes.length; i++) { shuffleSequence[i] = (int) shuffleSequenceBytes[i]; }

				onShuffleReceived(shuffleSequence);
				break;
			case MOVE:
				int pieceId = (int) data[1];
				onOpponentMove(pieceId);
				break;
			case FINISHED:
				int time = 3;
				onOpponentFinished(time);
				break;
			case QUIT:
				onOpponentQuit();
				break;
		}
	}

	protected abstract void onOpponentReady();
	protected abstract void onDifficultyChoiceReceived(Difficulty difficulty);
	protected abstract void onImageChoiceReceived(int imageIndex);
	protected abstract void onShuffleReceived(int[] sequence);
	protected abstract void onOpponentMove(int pieceId);
	protected abstract void onOpponentFinished(int time);
	protected abstract void onOpponentQuit();

	protected void sendReady(){
		byte[] msg = { (byte) READY };
		sendMessage(msg);
	}

	protected void sendDiceRoll(int number){
		Log.d(TAG, "sendDiceRoll");
		byte[] msg = { (byte) DICE_ROLL, (byte) number };
		sendMessage(msg);
	}

	protected void sendImageChoice(int imageIndex){
		byte[] msg = { (byte) IMAGE_CHOICE, (byte) imageIndex };
		sendMessage(msg);
	}

	protected void sendDifficultyChoice(Difficulty difficulty){
		byte[] difficultyBytes = difficulty.name().getBytes();

		byte[] msg = new byte[difficultyBytes.length + 1];
		msg[0] =  (byte) DIFFICULTY_CHOICE;
		for (int i = 0; i < difficultyBytes.length; i++){ msg[i + 1] = difficultyBytes[i]; }

		sendMessage(msg);
	}

	protected void sendShuffleSequence(int[] sequence){
		byte[] msg = new byte[sequence.length + 1];
		msg[0] = (byte) SHUFFLE;
		for(int i = 0; i < sequence.length; i++){
			msg[i + 1] = (byte) sequence[i];	// shuffle id's wont be larger than 127, so no risk for overflow
		}

		sendMessage(msg);
	}

	protected void sendMove(int pieceId){
		byte[] msg = { (byte) MOVE, (byte) pieceId };
		sendMessage(msg);
	}

	protected void sendFinished(int time){
		byte[] timeByteArray = ByteUtils.intToByteArray(time);
		byte[] msg = { (byte) FINISHED, timeByteArray[0], timeByteArray[1], timeByteArray[2], timeByteArray[3] };
		sendMessage(msg);
	}

	protected void sendQuit(){
		byte[] msg = { (byte) QUIT };
		sendMessage(msg);
	}

	private void sendMessage(byte[] msg){
		Games.RealTimeMultiplayer.sendReliableMessage(googleApiClient, null, msg, roomId, opponent.getParticipantId());
	}

	// * MISC* //

	private void goToMainMenu(){
		startActivity(new Intent(this, MainMenuActivity.class));
	}

	private void leaveRoom() {
		if (roomId != null) {
			Games.RealTimeMultiplayer.leave(googleApiClient, this, roomId);
			roomId = null;
		}
	}
}
