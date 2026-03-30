(g=>{var h,a,k,p="The Google Maps JavaScript API",c="google",l="importLibrary",q="__ib__",m=document,b=window;b=b[c]||(b[c]={});var d=b.maps||(b.maps={}),r=new Set,e=new URLSearchParams,u=()=>h||(h=new Promise(async(f,n)=>{await (a=m.createElement("script"));e.set("libraries",[...r]+"");for(k in g)e.set(k.replace(/[A-Z]/g,t=>"_"+t[0].toLowerCase()),g[k]);e.set("callback",c+".maps."+q);a.src=`https://maps.${c}apis.com/maps/api/js?`+e;d[q]=f;a.onerror=()=>h=n(Error(p+" could not load."));a.nonce=m.querySelector("script[nonce]")?.nonce||"";m.head.append(a)}));d[l]?console.warn(p+" only loads once. Ignoring:",g):d[l]=(f,...n)=>r.add(f)&&u().then(()=>d[l](f,...n))})
({key: apiKey, v: "weekly"});

async function initMap() {
    // Request needed libraries.
    (await google.maps.importLibrary('places'));
    // Create the input HTML element, and append it.
    const placeAutocomplete = new google.maps.places.PlaceAutocompleteElement({});
    document.body.appendChild(placeAutocomplete);

    // gmp-select fires when a valid place is chosen → show button
    placeAutocomplete.addEventListener('gmp-select', async ({ placePrediction }) => {
        try {
            const place = placePrediction.toPlace();
            await place.fetchFields({ fields: ['displayName', 'formattedAddress', 'location'] });

            console.log("Place Name: " + place.displayName);
            console.log("Latitude: " + place.location.lat());
            console.log("Longitude: " + place.location.lng());
            console.log('Selected Place:', place.id);

            const placeJSON = place.toJSON();

            if (!isValidPlace(placeJSON)) {
                removeActionButton();
                return;
            }

            // Change to showActionButton(place) to display the button after selecting a place
            showActionButton(place, placeJSON);

        } catch (error) {
            console.error('Error fetching place details:', error);
            removeActionButton();
        }
    });

    // Fires when Google can't resolve the input
    placeAutocomplete.addEventListener('gmp-requesterror', () => {
        removeActionButton();
    });

    // MutationObserver catches when the component's value attribute is cleared
    const observer = new MutationObserver(() => {
        if (!placeAutocomplete.value || placeAutocomplete.value.trim() === '') {
            removeActionButton();
        }
    });
    observer.observe(placeAutocomplete, {
        attributes: true,
        attributeFilter: ['value']
    });
}

function isValidPlace(placeJSON) {
    return (
        placeJSON &&
        typeof placeJSON === 'object' &&
        typeof placeJSON.formattedAddress === 'string' &&
        placeJSON.formattedAddress.trim() !== '' &&
        placeJSON.location &&
        typeof placeJSON.location.lat === 'number' &&
        typeof placeJSON.location.lng === 'number'
    );
}

function showActionButton(place, placeJSON) {
    // Remove existing button if it exists, so it always reflects the latest place
    const existingButton = document.getElementById('pick-restaurant-btn');
    if (existingButton) existingButton.remove();

    const button = document.createElement('button');
    button.id = 'pick-restaurant-btn';
    button.type = 'button';
    button.textContent = 'Pick a Restaurant near me!';
    document.body.appendChild(button);

    button.addEventListener('click', () => {
        fetch('/search', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(placeJSON)
        });
    });
}

function removeActionButton() {
    const existingButton = document.getElementById('pick-restaurant-btn');
    if (existingButton) existingButton.remove();
}

initMap();