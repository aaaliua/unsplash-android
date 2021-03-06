package com.mikepenz.unsplash.activities;

import android.animation.Animator;
import android.app.ProgressDialog;
import android.app.WallpaperManager;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.util.Log;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;
import com.koushikdutta.ion.Response;
import com.koushikdutta.ion.future.ResponseFuture;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.FontAwesome;
import com.mikepenz.unsplash.R;
import com.mikepenz.unsplash.fragments.ImagesFragment;
import com.mikepenz.unsplash.models.Image;
import com.mikepenz.unsplash.other.CustomAnimatorListener;
import com.mikepenz.unsplash.other.CustomTransitionListener;
import com.mikepenz.unsplash.views.Utils;

import java.io.InputStream;


public class DetailActivity extends ActionBarActivity {

    private ImageView fabButton;
    private View titleContainer;
    private View titlesContainer;
    private Image selectedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        // Recover items from the intent
        final int position = getIntent().getIntExtra("position", 0);
        selectedImage = (Image) getIntent().getSerializableExtra("selected_image");

        titlesContainer = findViewById(R.id.activity_detail_titles);

        // Fab button
        fabButton = (ImageView) findViewById(R.id.activity_detail_fab);
        fabButton.setScaleX(0);
        fabButton.setScaleY(0);
        fabButton.setImageDrawable(new IconicsDrawable(this, FontAwesome.Icon.faw_photo).color(Color.WHITE).sizeDp(24));
        fabButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final ProgressDialog mProgressDialog = new ProgressDialog(DetailActivity.this);

                //prepare the call
                final ResponseFuture<InputStream> future =
                        Ion.with(DetailActivity.this)
                                .load(selectedImage.getHighResImage())
                                .progressDialog(mProgressDialog)
                                .asInputStream();

                //setup the progressdialog
                mProgressDialog.setTitle(R.string.dialog_setting_title);
                mProgressDialog.setMessage(getString(R.string.dialog_setting_text));
                mProgressDialog.setIndeterminate(false);
                mProgressDialog.setCancelable(true);
                mProgressDialog.setMax(100);
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        future.cancel(true);
                    }
                });
                mProgressDialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        future.cancel(true);
                    }
                });
                mProgressDialog.show();

                //set the callback and start downloading
                future.withResponse().setCallback(new FutureCallback<Response<InputStream>>() {
                    @Override
                    public void onCompleted(Exception e, Response<InputStream> result) {
                        if (e == null && result != null && result.getResult() != null) {
                            try {
                                WallpaperManager.getInstance(DetailActivity.this).setStream(result.getResult());
                            } catch (Exception ex) {
                                Log.e("un:splash", ex.toString());
                            }
                        }
                        mProgressDialog.dismiss();
                    }
                });
            }
        });

        // Image summary card
        FrameLayout contentCard = (FrameLayout) findViewById(R.id.card_view);
        Utils.configuredHideYView(contentCard);

        // Title container
        titleContainer = findViewById(R.id.activity_detail_title_container);
        Utils.configuredHideYView(titleContainer);

        // Define toolbar as the shared element
        final Toolbar toolbar = (Toolbar) findViewById(R.id.activity_detail_toolbar);
        Bitmap imageCoverBitmap = ImagesFragment.photoCache.get(position);
        toolbar.setBackground(new BitmapDrawable(getResources(), imageCoverBitmap));

        if (Build.VERSION.SDK_INT >= 21) {
            toolbar.setTransitionName("cover");

            // Add a listener to get noticed when the transition ends to animate the fab button
            getWindow().getSharedElementEnterTransition().addListener(sharedTransitionListener);
        } else {
            sharedTransitionListener.onTransitionEnd(null);
        }

        // Generate palette colors
        Palette.generateAsync(imageCoverBitmap, paletteListener);
    }


    /**
     * I use a listener to get notified when the enter transition ends, and with that notifications
     * build my own coreography built with the elements of the UI
     * <p/>
     * Animations order
     * <p/>
     * 1. The image is animated automatically by the SharedElementTransition
     * 2. The layout that contains the titles
     * 3. An alpha transition to show the text of the titles
     * 3. A scale animation to show the image info
     */
    private CustomTransitionListener sharedTransitionListener = new CustomTransitionListener() {

        @Override
        public void onTransitionEnd(Transition transition) {

            super.onTransitionEnd(transition);

            ViewPropertyAnimator showTitleAnimator = Utils.showViewByScale(titleContainer);
            showTitleAnimator.setListener(new CustomAnimatorListener() {

                @Override
                public void onAnimationEnd(Animator animation) {

                    super.onAnimationEnd(animation);
                    titlesContainer.startAnimation(AnimationUtils.loadAnimation(DetailActivity.this, R.anim.alpha_on));
                    titlesContainer.setVisibility(View.VISIBLE);

                    Utils.showViewByScale(fabButton).start();
                    //Utils.showViewByScale(imageInfoLayout).start();
                }
            });

            showTitleAnimator.start();
        }
    };

    @Override
    public void onBackPressed() {

        ViewPropertyAnimator hideTitleAnimator = Utils.hideViewByScaleXY(fabButton);

        titlesContainer.startAnimation(AnimationUtils.loadAnimation(DetailActivity.this, R.anim.alpha_off));
        titlesContainer.setVisibility(View.INVISIBLE);

        //Utils.hideViewByScaleY(imageInfoLayout);

        hideTitleAnimator.setListener(new CustomAnimatorListener() {

            @Override
            public void onAnimationEnd(Animator animation) {

                ViewPropertyAnimator hideFabAnimator = Utils.hideViewByScaleY(titleContainer);
                hideFabAnimator.setListener(new CustomAnimatorListener() {

                    @Override
                    public void onAnimationEnd(Animator animation) {

                        super.onAnimationEnd(animation);
                        coolBack();
                    }
                });
            }
        });

        hideTitleAnimator.start();
    }

    private Palette.PaletteAsyncListener paletteListener = new Palette.PaletteAsyncListener() {

        @Override
        public void onGenerated(Palette palette) {

            Palette.Swatch s = palette.getVibrantSwatch();
            if (s == null) {
                s = palette.getMutedSwatch();
            }

            if (s != null) {
                titleContainer.setBackgroundColor(s.getRgb());

                if (Build.VERSION.SDK_INT >= 21) {
                    getWindow().setStatusBarColor(s.getRgb());
                }
                //getWindow().setNavigationBarColor(vibrantSwatch.getRgb());

                //TextView summaryTitle = (TextView) findViewById(R.id.activity_detail_summary_title);
                //summaryTitle.setTextColor(vibrantSwatch.getRgb());

                TextView titleTV = (TextView) titleContainer.findViewById(R.id.activity_detail_title);
                titleTV.setTextColor(s.getTitleTextColor());
                titleTV.setText(selectedImage.getAuthor());

                TextView subtitleTV = (TextView) titleContainer.findViewById(R.id.activity_detail_subtitle);
                subtitleTV.setTextColor(s.getTitleTextColor());
                subtitleTV.setText(selectedImage.getReadableDate());

                ((TextView) titleContainer.findViewById(R.id.activity_detail_subtitle))
                        .setTextColor(s.getTitleTextColor());
            }
        }
    };

    private void coolBack() {
        try {
            super.onBackPressed();
        } catch (Exception e) {
            // TODO: workaround
        }
    }
}
