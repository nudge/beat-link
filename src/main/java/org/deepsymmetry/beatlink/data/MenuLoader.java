package org.deepsymmetry.beatlink.data;

import org.deepsymmetry.beatlink.CdjStatus;
import org.deepsymmetry.beatlink.dbserver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides support for navigating the menu hierarchy offered by the dbserver on a player for a particular media slot.
 * Note that for historical reasons, loading track metadata, playlists, and the full track list are performed by the
 * {@link MetadataFinder}, even though those are technically menu operations.
 *
 * @since 0.4.0
 *
 * @author James Elliott
 */
public class MenuLoader {

    private static final Logger logger = LoggerFactory.getLogger(MenuLoader.class);

    /**
     * Ask the specified player for its top-level menu of menus. This is only believed to work for media slots that
     * contain a rekordbox database; a different form of message is used for those that do not. We need to figure out
     * how to tell in advance whether one exists; there seems to be a packet type that causes a player to send some
     * sort of media-information response that may well be the key.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details, although it does not seem to have an effect on the root menu
     *
     * @return the entries in the top level menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestRootMenuFrom(final SlotReference slotReference, final int sortOrder)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting root menu.");
                        Message response = client.menuRequest(Message.KnownType.ROOT_MENU_REQ, Message.MenuIdentifier.MAIN_MENU, slotReference.slot,
                                new NumberField(sortOrder), new NumberField(0xffffff));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting root menu");
    }

    /**
     * Ask the specified player for a Playlist menu. This boils down to a call to
     * {@link MetadataFinder#requestPlaylistItemsFrom(int, CdjStatus.TrackSourceSlot, int, int, boolean)} asking for
     * the playlist folder with ID 0, but it is also made available here since this is likely where people will be
     * looking for the capability. To get the contents of individual playlists or sub-folders, pass the playlist or
     * folder ID obtained by calling this to that function.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     *
     * @return the playlists and folders in the playlist menu
     *
     * @see MetadataFinder#requestPlaylistItemsFrom(int, CdjStatus.TrackSourceSlot, int, int, boolean)
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestPlaylistMenuFrom(final SlotReference slotReference, final int sortOrder)
            throws Exception {

        return MetadataFinder.getInstance().requestPlaylistItemsFrom(slotReference.player, slotReference.slot, sortOrder,
                0, true);
    }

    /**
     * Ask the specified player for a History menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details, although it does not seem to have an effect on the history menu
     *
     * @return the entries in the history menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestHistoryMenuFrom(final SlotReference slotReference, final int sortOrder)
        throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting History menu.");
                        Message response = client.menuRequest(Message.KnownType.HISTORY_MENU_REQ, Message.MenuIdentifier.MAIN_MENU, slotReference.slot,
                                new NumberField(sortOrder));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting history menu");
    }

    /**
     * Ask the specified player a History playlist.
     *
     * @param slotReference the player and slot for which the playlist is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     * @param historyId identifies which history session's playlist is desired
     *
     * @return the entries in the history playlist
     *
     * @throws Exception if there is a problem obtaining the playlist
     */
    public List<Message> requestHistoryPlaylistFrom(final SlotReference slotReference, final int sortOrder, final int historyId)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting History playlist.");
                        Message response = client.menuRequest(Message.KnownType.TRACK_MENU_FOR_HISTORY_REQ, Message.MenuIdentifier.MAIN_MENU,
                                slotReference.slot, new NumberField(sortOrder), new NumberField(historyId));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting history playlist");
    }

    /**
     * Ask the specified player for a Track menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     *
     * @return the entries in the track menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestTrackMenuFrom(final SlotReference slotReference, final int sortOrder)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                return MetadataFinder.getInstance().getFullTrackList(slotReference.slot, client, sortOrder);
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting track menu");
    }

    /**
     * Ask the specified player for an Artist menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     *
     * @return the entries in the artist menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestArtistMenuFrom(final SlotReference slotReference, final int sortOrder)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting Artist menu.");
                        Message response = client.menuRequest(Message.KnownType.ARTIST_MENU_REQ, Message.MenuIdentifier.MAIN_MENU, slotReference.slot,
                                new NumberField(sortOrder));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting artist menu");
    }

    /**
     * Ask the specified player for an Artist Album menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     * @param artistId  the artist whose album menu is desired
     *
     * @return the entries in the artist album menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestArtistAlbumMenuFrom(final SlotReference slotReference, final int sortOrder, final int artistId)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting Artist Album menu.");
                        Message response = client.menuRequest(Message.KnownType.ALBUM_MENU_FOR_ARTIST_REQ, Message.MenuIdentifier.MAIN_MENU, slotReference.slot,
                                new NumberField(sortOrder), new NumberField(artistId));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting artist album menu");
    }

    /**
     * Ask the specified player for an Artist Album Tracks menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     * @param artistId the artist whose album track menu is desired
     * @param albumId the album whose track menu is desired, or -1 for all albums
     *
     * @return the entries in the artist albums menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestArtistAlbumTrackMenuFrom(final SlotReference slotReference, final int sortOrder, final int artistId, final int albumId)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting Artist Album Track menu.");
                        Message response = client.menuRequest(Message.KnownType.TRACK_MENU_FOR_ARTIST_AND_ALBUM, Message.MenuIdentifier.MAIN_MENU,
                                slotReference.slot, new NumberField(sortOrder), new NumberField(artistId), new NumberField(albumId));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting artist album tracks menu");
    }

    /**
     * Ask the specified player for an Album Track menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     * @param albumId the album whose track menu is desired
     *
     * @return the entries in the album track menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestAlbumTrackMenuFrom(final SlotReference slotReference, final int sortOrder, final int albumId)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting Album Track menu.");
                        Message response = client.menuRequest(Message.KnownType.TRACK_MENU_FOR_ALBUM_REQ, Message.MenuIdentifier.MAIN_MENU, slotReference.slot,
                                new NumberField(sortOrder), new NumberField(albumId));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting album tracks menu");
    }

    /**
     * Ask the specified player for a Genre menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     *
     * @return the entries in the genre menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestGenreMenuFrom(final SlotReference slotReference, final int sortOrder)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting Genre menu.");
                        Message response = client.menuRequest(Message.KnownType.GENRE_MENU_REQ, Message.MenuIdentifier.MAIN_MENU, slotReference.slot,
                                new NumberField(sortOrder));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting genre menu");
    }

    /**
     * Ask the specified player for a Genre Artists menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     * @param genreId the genre whose artist menu is desired
     *
     * @return the entries in the genre artists menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestGenreArtistMenuFrom(final SlotReference slotReference, final int sortOrder, final int genreId)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting Genre Artist menu.");
                        Message response = client.menuRequest(Message.KnownType.ARTIST_MENU_FOR_GENRE_REQ, Message.MenuIdentifier.MAIN_MENU, slotReference.slot,
                                new NumberField(sortOrder), new NumberField(genreId));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting genre artists menu");
    }

    /**
     * Ask the specified player for a Genre Artist Albums menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     * @param genreId the genre whose artist album menu is desired
     * @param artistId the artist whose album menu is desired, or -1 for all artists
     *
     * @return the entries in the genre artist albums menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestGenreArtistAlbumMenuFrom(final SlotReference slotReference, final int sortOrder, final int genreId, final int artistId)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting Genre Artist Album menu.");
                        Message response = client.menuRequest(Message.KnownType.ALBUM_MENU_FOR_GENRE_AND_ARTIST, Message.MenuIdentifier.MAIN_MENU,
                                slotReference.slot, new NumberField(sortOrder), new NumberField(genreId), new NumberField(artistId));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting genre artist albums menu");
    }

    /**
     * Ask the specified player for a Genre Artist Album Tracks menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     * @param genreId the genre whose artist album track menu is desired
     * @param artistId the artist whose album track menu is desired, or -1 for all artists
     * @param albumId the album whose track menu is desired, or -1 for all albums
     *
     * @return the entries in the genre artist albums menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestGenreArtistAlbumTrackMenuFrom(final SlotReference slotReference, final int sortOrder, final int genreId,
                                                              final int artistId, final int albumId)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting Genre Artist Album Track menu.");
                        Message response = client.menuRequest(Message.KnownType.TRACK_MENU_FOR_GENRE_ARTIST_AND_ALBUM, Message.MenuIdentifier.MAIN_MENU,
                                slotReference.slot, new NumberField(sortOrder), new NumberField(genreId), new NumberField(artistId), new NumberField(albumId));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting genre artist album tracks menu");
    }

    /**
     * Ask the specified player for an Album menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     *
     * @return the entries in the album menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestAlbumMenuFrom(final SlotReference slotReference, final int sortOrder)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting Album menu.");
                        Message response = client.menuRequest(Message.KnownType.ALBUM_MENU_REQ, Message.MenuIdentifier.MAIN_MENU, slotReference.slot,
                                new NumberField(sortOrder));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting album menu");
    }

    /**
     * Ask the specified player for a Key menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     *
     * @return the entries in the key menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestKeyMenuFrom(final SlotReference slotReference, final int sortOrder)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting Key menu.");
                        Message response = client.menuRequest(Message.KnownType.KEY_MENU_REQ, Message.MenuIdentifier.MAIN_MENU, slotReference.slot,
                                new NumberField(sortOrder));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting key menu");
    }

    /**
     * Ask the specified player for a key neighbor menu for a given key.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param keyId the key whose available compatible keys are desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     *
     * @return the entries in the key neighbor menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestKeyNeighborMenuFrom(final SlotReference slotReference, final int sortOrder, final int keyId)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting key neighbor menu.");
                        Message response = client.menuRequest(Message.KnownType.NEIGHBOR_MENU_FOR_KEY, Message.MenuIdentifier.MAIN_MENU,
                                slotReference.slot, new NumberField(sortOrder), new NumberField(keyId));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting key neighbor menu");
    }

    /**
     * Ask the specified player for a track menu for an allowed distance from a given key.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param keyId the key whose compatible tracks are desired
     * @param distance how far along the circle of fifths are the tracks allowed to be from the specified key
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     *
     * @return the matching tracks
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestTracksByKeyAndDistanceFrom(final SlotReference slotReference, final int sortOrder, final int keyId, final int distance)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting key neighbor menu.");
                        Message response = client.menuRequest(Message.KnownType.TRACK_MENU_FOR_KEY_AND_DISTANCE, Message.MenuIdentifier.MAIN_MENU,
                                slotReference.slot, new NumberField(sortOrder), new NumberField(keyId), new NumberField(distance));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting tracks by key and distance menu");
    }

    /**
     * Ask the specified player for a BPM menu.
     *
     * @param slotReference the player and slot for which the menu is desired
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details
     *
     * @return the entries in the BPM menu
     *
     * @throws Exception if there is a problem obtaining the menu
     */
    public List<Message> requestBpmMenuFrom(final SlotReference slotReference, final int sortOrder)
            throws Exception {

        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
                    try {
                        logger.debug("Requesting BPM menu.");
                        Message response = client.menuRequest(Message.KnownType.BPM_MENU_REQ, Message.MenuIdentifier.MAIN_MENU, slotReference.slot,
                                new NumberField(sortOrder));
                        return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slotReference.slot, CdjStatus.TrackType.REKORDBOX, response);
                    } finally {
                        client.unlockForMenuOperations();
                    }
                } else {
                    throw new TimeoutException("Unable to lock player for menu operations.");
                }
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(slotReference.player, task, "requesting BPM menu");
    }

    /**
     * Ask the connected dbserver about database records whose names contain {@code text}. If {@code count} is not
     * {@code null}, no more than that many results will be returned, and the value will be set to the total number
     * of results that were available. Otherwise all results will be returned.
     *
     * @param slot the slot in which the database can be found
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details, although it does not seem to have an effect on searches.
     * @param text the search text used to filter the results
     * @param count if present, sets an upper limit on the number of results to return, and will get set
     *              to the actual number that were available
     *
     * @return the items that match the specified search string; they may be a variety of different types
     *
     * @throws IOException if there is a problem communicating
     * @throws InterruptedException if the thread is interrupted while trying to lock the client for menu operations
     * @throws TimeoutException if we are unable to lock the client for menu operations
     */
    private List<Message> getSearchItems(CdjStatus.TrackSourceSlot slot, int sortOrder, String text,
                                         AtomicInteger count, Client client)
            throws IOException, InterruptedException, TimeoutException {
        if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
            try {
                final StringField textField = new StringField(text);
                Message response = client.menuRequest(Message.KnownType.SEARCH_MENU, Message.MenuIdentifier.MAIN_MENU, slot,
                        new NumberField(sortOrder), new NumberField(textField.getSize()), textField, NumberField.WORD_0);
                final int actualCount = (int)response.getMenuResultsCount();
                if (actualCount == Message.NO_MENU_RESULTS_AVAILABLE || actualCount == 0) {
                    if (count != null) {
                        count.set(0);
                    }
                    return Collections.emptyList();
                }

                // Gather the requested number of search menu items
                if (count == null) {
                    return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slot, CdjStatus.TrackType.REKORDBOX, response);
                } else {
                    final int desiredCount = Math.min(count.get(), actualCount);
                    count.set(actualCount);
                    if (desiredCount < 1) {
                        return Collections.emptyList();
                    }
                    return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slot, CdjStatus.TrackType.REKORDBOX,
                            0, desiredCount);
                }
            } finally {
                client.unlockForMenuOperations();
            }
        } else {
            throw new TimeoutException("Unable to lock player for menu operations.");
        }
    }

    /**
     * Ask the specified player for database records whose names contain {@code text}. If {@code count} is not
     * {@code null}, no more than that many results will be returned, and the value will be set to the total number
     * of results that were available. Otherwise all results will be returned.
     *
     * @param player the player number whose database is to be searched
     * @param slot the slot in which the database can be found
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details, although it does not seem to have an effect on searches.
     * @param text the search text used to filter the results
     * @param count if present, sets an upper limit on the number of results to return, and will get set
     *              to the actual number that were available
     *
     * @return the items that the specified search string; they may be a variety of different types
     *
     * @throws Exception if there is a problem performing the search
     */
    public List<Message> requestSearchResultsFrom(final int player, final CdjStatus.TrackSourceSlot slot,
                                                  final int sortOrder, final String text,
                                                  final AtomicInteger count)
            throws Exception {
        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                return getSearchItems(slot, sortOrder, text.toUpperCase(), count, client);
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(player, task, "performing search");
    }


    /**
     * Ask the connected dbserver about database records whose names contain {@code text}. If {@code count} is not
     * {@code null}, no more than that many results will be returned, and the value will be set to the total number
     * of results that were available. Otherwise all results will be returned.
     *
     * @param slot the slot in which the database can be found
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details, although it does not seem to have an effect on searches.
     * @param text the search text used to filter the results
     * @param offset the first result desired (the first available result has offset 0)
     * @param count the number of results to return (if more than the number available, fewer will simply be returned)
     *
     * @return the items that match the specified search string; they may be a variety of different types
     *
     * @throws IOException if there is a problem communicating
     * @throws InterruptedException if the thread is interrupted while trying to lock the client for menu operations
     * @throws TimeoutException if we are unable to lock the client for menu operations
     */
    private List<Message> getMoreSearchItems(final CdjStatus.TrackSourceSlot slot, final int sortOrder, final String text,
                                             final int offset, final int count, final Client client)
            throws IOException, InterruptedException, TimeoutException {
        if (client.tryLockingForMenuOperations(MetadataFinder.MENU_TIMEOUT, TimeUnit.SECONDS)) {
            try {
                final StringField textField = new StringField(text);
                Message response = client.menuRequest(Message.KnownType.SEARCH_MENU, Message.MenuIdentifier.MAIN_MENU, slot,
                        new NumberField(sortOrder), new NumberField(textField.getSize()), textField, NumberField.WORD_0);
                final int actualCount = (int)response.getMenuResultsCount();
                if (offset + count > actualCount) {
                    throw new IllegalArgumentException("Cannot request items past the end of the menu.");
                }

                // Gather the requested search menu items
                return client.renderMenuItems(Message.MenuIdentifier.MAIN_MENU, slot, CdjStatus.TrackType.REKORDBOX,
                        offset, count);
            } finally {
                client.unlockForMenuOperations();
            }
        } else {
            throw new TimeoutException("Unable to lock player for menu operations.");
        }
    }

    /**
     * Ask the specified player for more database records whose names contain {@code text}. This can be used after
     * calling {@link #requestSearchResultsFrom(int, CdjStatus.TrackSourceSlot, int, String, AtomicInteger)} to obtain
     * a partial result and the total count available, to gradually expand the search under direction from the user.
     *
     * @param player the player number whose database is to be searched
     * @param slot the slot in which the database can be found
     * @param sortOrder the order in which responses should be sorted, 0 for default, see Section 6.11.1 of the
     *                  <a href="https://github.com/brunchboy/dysentery/blob/master/doc/Analysis.pdf">Packet Analysis
     *                  document</a> for details, although it does not seem to have an effect on searches.
     * @param text the search text used to filter the results
     * @param offset the first result desired (the first available result has offset 0)
     * @param count the number of results to return (if more than the number available, fewer will simply be returned)
     *
     * @return the items that the specified search string; they may be a variety of different types
     *
     * @throws Exception if there is a problem performing the search
     */
    public List<Message> requestMoreSearchResultsFrom(final int player, final CdjStatus.TrackSourceSlot slot,
                                                      final int sortOrder, final String text,
                                                      final int offset, final int count)
            throws Exception {
        ConnectionManager.ClientTask<List<Message>> task = new ConnectionManager.ClientTask<List<Message>>() {
            @Override
            public List<Message> useClient(Client client) throws Exception {
                return getMoreSearchItems(slot, sortOrder, text.toUpperCase(), offset, count, client);
            }
        };

        return ConnectionManager.getInstance().invokeWithClientSession(player, task, "performing search");
    }


    /**
     * Holds the singleton instance of this class.
     */
    private static final MenuLoader ourInstance = new MenuLoader();

    /**
     * Get the singleton instance of this class.
     *
     * @return the only instance of this class which exists.
     */
    public static MenuLoader getInstance() {
        return ourInstance;
    }

    /**
     * Prevent direct instantiation.
     */
    private MenuLoader() {
        // Nothing to do.
    }
}