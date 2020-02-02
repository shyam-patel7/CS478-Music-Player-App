/* CS478 Project 5: Services
 * Name:   Shyam Patel
 * NetID:  spate54
 * Date:   Dec 9, 2019
 */

package com.proj5.services.MediaPlaybackCommon;

interface Interface {
    boolean play(int id);
    boolean pause();
    boolean resume();
    boolean stop();
    boolean isPlaying();
    boolean isPaused();
    int     getProgress();
    String  getTime();
}//end Interface
