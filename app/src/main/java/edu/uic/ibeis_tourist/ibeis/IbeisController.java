package edu.uic.ibeis_tourist.ibeis;

import android.content.Context;
import android.os.AsyncTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import edu.uic.ibeis_java_api.api.Ibeis;
import edu.uic.ibeis_java_api.api.IbeisAnnotation;
import edu.uic.ibeis_java_api.api.IbeisImage;
import edu.uic.ibeis_java_api.api.IbeisIndividual;
import edu.uic.ibeis_java_api.api.IbeisQueryResult;
import edu.uic.ibeis_java_api.api.IbeisQueryScore;
import edu.uic.ibeis_java_api.values.Sex;
import edu.uic.ibeis_java_api.values.Species;
import edu.uic.ibeis_tourist.exceptions.MatchNotFoundException;
import edu.uic.ibeis_tourist.local_database.LocalDatabase;
import edu.uic.ibeis_tourist.model.Location;
import edu.uic.ibeis_tourist.model.PictureInfo;
import edu.uic.ibeis_tourist.model.Position;
import edu.uic.ibeis_tourist.model.SexEnum;
import edu.uic.ibeis_tourist.model.SpeciesEnum;
import edu.uic.ibeis_tourist.utils.ImageUtils;


public class IbeisController implements edu.uic.ibeis_tourist.interfaces.IbeisInterface {

    private static final int BROOKFIELD_GIRAFFES_ENCOUNTER_ID = 99;
    private static final int QUERY_RECOGNITION_THRESHOLD = 12;

    @Override
    public void identifyIndividual(String fileName, Location location, Position position,
                                   GregorianCalendar dateTime, Context context) throws MatchNotFoundException {

        System.out.println("IbeisController: Identify Individual");
        new IdentifyIndividualAsyncTask(fileName, position, dateTime, location, context).execute();
    }

    // AsyncTask classes implementation

    private class IdentifyIndividualAsyncTask extends AsyncTask<Void, Void, PictureInfo> {

        private Ibeis ibeis;

        private String mFileName;
        private Location mLocation;
        private Position mPosition;
        private GregorianCalendar mDateTime;
        private Context mContext;

        private IdentifyIndividualAsyncTask(String fileName, Position position, GregorianCalendar dateTime,
                                            Location location, Context context) {

            System.out.println("IdentifyIndividualAsyncTask");

            ibeis = new Ibeis();

            mFileName = fileName;
            mLocation = location;
            mPosition = position;
            mDateTime = dateTime;
            mContext = context;
        }

        // TODO temporary implementation
        @Override
        protected PictureInfo doInBackground(Void... params) {
            System.out.println("IbeisInterfaceImplementation: IdentifyIndividual AsyncTask");

            PictureInfo pictureInfo = new PictureInfo();
            pictureInfo.setFileName(mFileName);
            pictureInfo.setLocation(mLocation);
            pictureInfo.setPosition(mPosition);
            pictureInfo.setDateTime(mDateTime);
            pictureInfo.setIndividualName(null);
            pictureInfo.setIndividualSpecies(SpeciesEnum.UNKNOWN);
            pictureInfo.setIndividualSex(SexEnum.UNKNOWN);

            try {
                //upload image to IBEIS
                IbeisImage image = ibeis.uploadImage(new File(ImageUtils.PATH_TO_IMAGE_FILE + mFileName));

                //run animal detection for giraffes
                List<IbeisAnnotation> imageAnnotations = ibeis.runAnimalDetection(image, Species.GIRAFFE);

                if(imageAnnotations.size() > 0) {//if at least an annotation is found
                    pictureInfo.setIndividualSpecies(SpeciesEnum.GIRAFFE);

                    //TODO handle multiple annotations (temporary solution: the query annotation is the first annotation in the list)
                    IbeisAnnotation queryAnnotation = imageAnnotations.get(0);

                    List<IbeisAnnotation> dbAnnotations = new ArrayList<>();
                    for(IbeisIndividual i : ibeis.getEncounterById(BROOKFIELD_GIRAFFES_ENCOUNTER_ID).getIndividuals()) {
                        dbAnnotations.addAll(i.getAnnotations());
                    }
                    IbeisQueryResult queryResult = ibeis.query(queryAnnotation, dbAnnotations);
                    List<IbeisQueryScore> queryScores = queryResult.getScores();

                    //sort query scores from the highest to the lowest
                    Collections.sort(queryScores, Collections.reverseOrder());
                    //get the highest score
                    IbeisQueryScore highestScore = queryScores.get(0);

                    if(highestScore.getScore() > QUERY_RECOGNITION_THRESHOLD) {
                        IbeisIndividual individual = highestScore.getDbAnnotation().getIndividual();
                        pictureInfo.setIndividualName(individual.getName());

                        Sex individualSex = individual.getSex();
                        pictureInfo.setIndividualSex(
                                (individualSex == Sex.MALE) ? SexEnum.MALE :
                                        (individualSex == Sex.FEMALE) ? SexEnum.FEMALE :
                                                SexEnum.UNKNOWN);
                    }
                }
            } catch (Exception e) {//TODO handle exceptions
                e.printStackTrace();
            }
            return pictureInfo;
        }

        @Override
        protected void onPostExecute(PictureInfo pictureInfo) {
            super.onPostExecute(pictureInfo);

            System.out.println("IdentifyIndividualAsyncTask: onPostExecute");
            LocalDatabase localDb = new LocalDatabase();
            localDb.addPicture(pictureInfo, mContext);
        }
    }
}
