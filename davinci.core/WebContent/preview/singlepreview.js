dojo.provide('preview.singlepreview');
dojo.require('preview.silhouetteiframe');
dojo.require('dijit._Widget');
dojo.require("dijit.form.Button");
dojo.require("dijit.form.Slider");
dojo.require("dijit.form.Select");

dojo.require("dojo.i18n");  
dojo.requireLocalization("preview", "preview");

dojo.declare("preview.singlepreview", [dijit._Widget], {

	devicelist:null,
	currentDevice:0,
	currentZoom:1,
	iframefilename:null,
	orientation:'portrait',
	scalefactor:1,
	showZoom:true,
	margin:0,
	pathToPreviewerFolder:'preview/',
	silhouetteiframe:null,
	silhouetteiframe_connect_onload:null,
	// make sure browsers don't used cache iframe contents from last time
	_randomUrlParam:'&'+Math.random()+'=1', 
	
	buildRendering: function() {
		this._connects=[];
		var langObj = dojo.i18n.getLocalization("preview", "preview");
		if(this.devicelist && this.devicelist.length>0 && this.iframefilename){
			this.addStyleDeclarations();
			this.domNode = this.srcNodeRef;
			var s='<table border="0" cellspacing="0" cellpadding="0">'+
				//FIXME: pushed to text-align:left to avoid layout jumping upon zooming
				//but maybe can restore to text-align:center if clever tricks prevent
				//layout jumping even if centered
					'<tr><td style="text-align:left;">'+
					'<div class="controlbar">'+
					'<span class="controlbar_label">'+langObj.device+'</span>'+
					'<span class="controlbar_container controlbar_container_device"><span class="controlbar_device"></span></span>'+
					'<span class="controlbar_label controlbar_label_zoom">'+langObj.zoom+'</span>'+
					'<span class="controlbar_container controlbar_container_zoom"><span class="controlbar_zoom_labels"></span><span class="controlbar_zoom"></span></span>'+
					'<span class="controlbar_label">'+langObj.angle+'</span>'+
					'<span class="controlbar_container controlbar_container_angle"><span class="controlbar_angle"></span></span>'+
					'</div>'+
					'</td></tr>'+
				//FIXME: pushed to text-align:left to avoid layout jumping upon zooming
				//but maybe can restore to text-align:center if clever tricks prevent
				//layout jumping even if centered
					'<tr><td style="text-align:left;">'+
				//FIXME: replace inline style with something in a stylesheet
					'<div class="silhouette_container" style="display:inline-block"></div>'+
					'</td></tr></table>';
			this.domNode.innerHTML = s;
			if(!this.showZoom){
				dojo.query('.controlbar_label_zoom',this.domNode)[0].style.display='none';
				dojo.query('.controlbar_container_zoom',this.domNode)[0].style.display='none';
			}
			this.initControls();
		}else{
			console.log('preview.singlepreview.buildRendering(): Missing required parameters devicelist and iframefilename');
		};
	},

	addStyleDeclarations: function(){
		// Only add style declarations if not already there
		var style_elems = dojo.query('style.singlepreview_styles');
		if(style_elems.length==0){
			var head_elem = document.querySelectorAll('head')[0];
			if(!head_elem){
				console.error('silhouetteiframe.js addStyleDeclarations(): no HEAD element');
				return;
			}
			dojo.create('style',{
				type:'text/css',
				'class':'singlepreview_styles',
				innerHTML:''+
					'.controlbar { line-height:1.25em; }\n'+
					'.controlbar_label { display:inline-block; vertical-align:middle; margin-top:14px; }\n'+
					'.controlbar_container { display:inline-block; vertical-align:top; }\n'+
					'.controlbar_container_device { margin:9px 7px 0 1px; }\n'+
					'.controlbar_container_zoom { margin-right:6px; }\n'+
					'.controlbar_container_angle { margin:9px 9px 0 1px; }\n'+
					'.control_angle { width:16px; height:16px; }\n'+
					'.control_angle_cw { background-image:url('+this.pathToPreviewerFolder+'images/rotate_cw.png); }\n'+
					'.control_angle_ccw { background-image:url('+this.pathToPreviewerFolder+'images/rotate_ccw.png); }\n'+
					'.controlbar .controlbar_container.controlbar_container_angle .dijitButtonNode { padding: 0px; }'
			},head_elem);
		}
	},

	initControls: function(){
		var spw = this;	// spw = single preview widget -- used by out-of-scope callbacks below

		var select_device_node = dojo.query('.controlbar_device',this.domNode)[0];
		var device_select = this.device_select = 
			new dijit.form.Select({},select_device_node);
		device_select.addOption(this.devicelist);
		// Timeout to prevent initial widget loading from trigger onChange handler
		setTimeout(function(){
			spw.connect(device_select, 'onChange', function(newvalue){
				if(newvalue != spw.currentDevice){
					spw.currentDevice = newvalue;
					var theme = preview.silhouetteiframe.getMobileTheme(spw.devicelist[spw.currentDevice].file);
					var iframefilename_with_params = spw.iframefilename+'?theme='+theme+this._randomUrlParam;
					this.update_silhouette_container(iframefilename_with_params);
				}
			});
		},1);

		if(this.showZoom){
			var zoom_node = dojo.query('.controlbar_zoom',this.domNode)[0];
			var zoom_labels_node = dojo.query('.controlbar_zoom_labels',this.domNode)[0];
			var sliderLabels = this.sliderLabels = 
				new dijit.form.HorizontalRuleLabels({
					container: "topDecoration",
					count: 14,
					labels: ['',0,'','',1,'','',2,'','',3,''],
					style: "height:1.5em;font-size:100%;color:gray; width:74px"
				},zoom_labels_node);
			var zoom_select = this.zoom_select = 
				new dijit.form.HorizontalSlider({
					value: this.currentZoom,
					minimum: .2,
					maximum: 3,
					intermediateChanges: true,
					showButtons: false,
					style: "width:75px;"
				},
				zoom_node);
			this.connect(zoom_select, 'onChange', function(){
				if(zoom_select.value != spw.currentZoom){
					spw.currentZoom = zoom_select.value;
					spw.silhouetteiframe.setScaleFactor(spw.currentZoom);
				}
			});
		}

		var angle_node = dojo.query('.controlbar_angle',this.domNode)[0];
		var cw_ccw_class = (spw.orientation == 'landscape') ? 'control_angle_ccw' : 'control_angle_cw';
		var angle_select = this.angle_select = 
			new dijit.form.Button({ 
				iconClass:"control_angle "+cw_ccw_class,
				showLabel:false,
				style:"width:16px; height:16px;"
			},angle_node);
		this.connect(angle_select, 'onClick', function(){
			var iconnode = dojo.query('.control_angle',spw.domNode)[0];
			if(spw.orientation == 'landscape'){
				spw.orientation = 'portrait';
				dojo.removeClass(iconnode, 'control_angle_ccw');
				dojo.addClass(iconnode, 'control_angle_cw');
			}else{
				spw.orientation = 'landscape';
				dojo.removeClass(iconnode, 'control_angle_cw');
				dojo.addClass(iconnode, 'control_angle_ccw');
			}
			spw.silhouetteiframe.setOrientation(spw.orientation);	
		});
		var theme = preview.silhouetteiframe.getMobileTheme(this.devicelist[this.currentDevice].file);
		var iframefilename_with_params = this.iframefilename+'?theme='+theme+this._randomUrlParam;
		this.update_silhouette_container(iframefilename_with_params);
	},

	update_silhouette_container: function(iframefilename_with_params){
		var spw = this;	// spw = single preview widget -- used by out-of-scope callbacks below
		if(this.silhouetteiframe_connect_onload){
			dojo.disconnect(this.silhouetteiframe_connect_onload);
			this.silhouetteiframe_connect_onload=null;
		}
		var silhouette_container = dojo.query(".silhouette_container",this.domNode)[0];
		silhouette_container.innerHTML = '<div class="silhouette_div_container">'+
			'<span class="silhouetteiframe_object_container"></span>'+
			'<iframe src="'+iframefilename_with_params+'" class="silhouetteiframe_iframe"></iframe>'+
			'</div>';
		var silhouette_div_container=dojo.query('.silhouette_div_container',silhouette_container)[0];
		
		// Don't start rendering silhouette until iframe is loaded
		var silhouetteiframe_iframe=dojo.query('.silhouetteiframe_iframe',silhouette_container)[0];
		this.silhouetteiframe_connect_onload = dojo.connect(silhouetteiframe_iframe, 'onload', function(){
			spw.silhouetteiframe = new preview.silhouetteiframe({
				rootNode:silhouette_div_container,
				svgfilename:spw.devicelist[spw.currentDevice].file,
				orientation:orientation,
				scalefactor:scalefactor,
				margin:spw.margin
			});
		});	
	},
	
	destroy: function(preserveDom){
		if(this.silhouetteiframe_connect_onload){
			dojo.disconnect(this.silhouetteiframe_connect_onload);
			this.silhouetteiframe_connect_onload=null;
		}
		this.device_select.destroy();
		this.sliderLabels.destroy();
		this.zoom_select.destroy();
		this.angle_select.destroy();
		this.inherited("destroy",arguments);
	}

});

