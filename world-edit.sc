//World edit

__config()->{
    'commands'->{
        'fill <block>'->['fill',null],
        'fill <block> <replacement>'->'fill',
        'undo'->['undo', 1],
        'undo all'->['undo', 0],
        'undo <moves>'->'undo',
        'redo'->['redo', 1],
        'redo all'->['redo', 0],
        'redo <moves>'->'redo',
        'undo history'->'print_history',
        'wand <wand>'->_(wand)->(global_wand=wand:0),
        'rotate <pos> <degrees> <axis>'->'rotate',//will replace old stuff if need be
        'stack'->['stack',1,null],
        'stack <stackcount>'->['stack',null],
        'stack <stackcount> <direction>'->'stack',
        'expand <pos> <magnitude>'->'expand',
        'clone <pos>'->['clone',false],
        'move <pos>'->['clone',true]
    },
    'arguments'->{
        'replacement'->{'type'->'blockpredicate'},
        'moves'->{'type'->'int','min'->1,'suggest'->[]},//todo decide on whether or not to add max undo limit
        'degrees'->{'type'->'int','suggest'->[]},
        'axis'->{'type'->'term','options'->['x','y','z']},
        'wand'->{'type'->'item','suggest'->['wooden_sword','wooden_axe']},
        'direction'->{'type'->'term','options'->['north','south','east','west','up','down']},
        'stackcount'->{'type'->'int','min'->1,'suggest'->[]},
        'magnitude'->{'type'->'float','suggest'->[1,2,0.5]},
        'flags'->{'type'->'text'}
    }
};
//player globals

global_wand = 'wooden_sword';
global_history = [];
global_undo_history = [];
global_positions = [];


//Extra boilerplate

global_affected_blocks=[];

//Block-selection

_select_pos(player,pos)->(//in case first position is not selected
    if(length(global_player_data:'positions')==0,
        global_player_data:'positions':0=pos;
        _print(player, 'pos1', pos),
        global_player_data:'positions':1=pos;
        _print(player, 'pos2', pos)
    )
);

__on_player_clicks_block(player, block, face) ->(
    if(player~'holds':0==global_player_data:'wand',
        global_player_data:'positions':0=pos(block);
        _print(player, 'pos1', pos(block))
    )
);

__on_player_breaks_block(player, block) ->(//incase we made an oopsie with a non-sword want item
    if(player~'holds':0==global_wand,
        global_positions:0=pos(block);
        schedule(0,_(block)->without_updates(set(pos(block),block)),block);
        _print(player, 'pos1', pos)
    )
);

__on_player_uses_item(player, item_tuple, hand) ->(
    if(item_tuple:0==global_wand&&(block=query(player,'trace',128,'blocks')),
        _select_pos(player, pos(block))
    )
);

//Misc functions

//Config Parser

_parse_config(config) -> (
    if(type(config) != 'list', config = [config]);
    ret = {};
    for(config,
        if(_ ~ '^\\w+ ?= *.+$' != null,
            key = _ ~ '^\\w+(?= ?= *.+)';   
            value = _ ~ ('(?<='+key+' ?= ?) *([^ ].*)');
            ret:key = value
        )
    );
    ret
);

//Translations

global_lang_ids = ['en_us'];
global_langs = {};
for(global_lang_ids,
    global_langs:_ = read_file(_, 'text');
    if(global_langs:_ == null, 
        write_file(_, 'text', global_langs:_ = [
            'language_code =    en_us',
            'language =         english',

            'pos1 =             w Set first position to %s',                             // [x, y, z]
            'pos2 =             w Set second position to %s',                            // [x, y, z]
            'nopos =            r No points selected for player %s',                     // player 
            'filled =           gi Filled %d blocks',                                    // blocks number 
            'no_undo_history =  w No undo history to show for player %s',                // player 
            'many_undo =        w Undo history for player %s is very long, showing only the last ten items', // player 
            'entry_undo =       w %d: type: %s\\n    affected positions: %s',             // index, command type, blocks number
            'no_undo =          r No actions to undo for player %s',                     // player
            'more_moves_undo =  w Too many moves to undo, undoing all moves for %s',     // player
            'success_undo =     gi Successfully undid %d operations, filling %d blocks', // moves number, blocks number
        ])
    );
    global_langs:_ = _parse_config(global_langs:_)
);
_translate(key, replace_list) -> (
    print(player(),key+' '+replace_list);
    lang_id = global_player_data:'lang';
    if(lang_id == null || !has(global_lang_ids, lang_id),
        lang_id = global_lang_ids:0);
    str(global_langs:lang_id:key, replace_list)
);
_print(player, key, ... replace) -> print(player, format(_translate(key, replace)));


//Command processing functions

set_block(pos,block,replacement)->(//use this function to set blocks
    success=null;
    existing = block(pos);
    if(block != existing && (!replacement || _block_matches(existing, replacement) ),
        postblock=set(existing,block);
        success=existing;
        global_affected_blocks+=[pos,postblock,existing];//todo decide whether to replace all blocks or only blocks that were there before action (currently these are stored, but that may change if we dont want them to)
    );
    bool(success)//cos undo uses this
);

_block_matches(existing, block_predicate) ->
(
    [name, block_tag, properties, nbt] = block_predicate;

    (name == null || name == existing) &&
    (block_tag == null || block_tags(existing, block_tag)) &&
    all(properties, block_state(existing, _) == properties:_) &&
    (!tag || tag_matches(block_data(existing), tag))
);

_get_player_positions(player)->(
    pos=global_positions;
    if(length(pos)==0,
        exit(_print(player, 'nopos', player))
    );
    start_pos=pos:0;
    end_pos=if(pos:1,pos:1,pos(player));
    [start_pos,end_pos]
);

add_to_history(function,player)->(

    if(length(global_affected_blocks)==0,return());//not gonna add empty list to undo ofc...
    command={
        'type'->function,
        'affected_positions'->global_affected_blocks
    };

    _print(player,'filled',length(global_affected_blocks));
    global_affected_blocks=[];
    global_history+=command;
);

print_history()->(
    player=player();
    history = global_player_data:'history';
    if(length(history)==0||history==null,_print(player, 'no_undo_history', player));
    if(length(history)>10,_print(player, 'many_undo', player));
    total=min(length(history),10);//total items to print
    for(range(total),
        command=history:(length(history)-(_+1));//getting last 10 items in reverse order
        _print(player, 'entry_undo', history~command+1,command:'type', length(command:'affected_positions'))
    )
);

//Command functions

undo(moves)->(
    player = player();
    history=global_player_data:'history';
    if(length(history)==0||history==null,exit(_print(player, 'no_undo', player)));//incase an op was running command, we want to print error to them
    if(length(history)<moves,_print(player, 'more_moves_undo', player);moves=0);
    if(moves==0,moves=length(history));
    for(range(moves),
        command = history:(length(history)-1);//to get last item of list properly

        for(command:'affected_positions',
            set_block(_:0,_:2,null)//todo decide whether to replace all blocks or only blocks that were there before action (currently these are stored, but that may change if we dont want them to)
        );

        delete(history,(length(history)-1))
    );
    global_undo_history+=global_affected_blocks;//we already know that its not gonna be empty before this, so no need to check now.
    print(player,format('gi Successfully undid '+moves+' operations, filling '+length(global_affected_blocks)+' blocks'));
    global_affected_blocks=[];
);

redo(moves)->(
    player=player();
    history=global_undo_history;
    if(length(history)==0||history==null,exit(print(player,format('r No actions to redo for player '+player))));
    if(length(history)<moves,print(player,'Too many moves to redo, redoing all moves for '+player);moves=0);
    if(moves==0,moves=length(history));
    for(range(moves),
        command = history:(length(history)-1);//to get last item of list properly

        for(command,
            set_block(_:0,_:2,null);
        );

        delete(history,(length(history)-1))
    );
    global_history+={'type'->'redo','affected_positions'->global_affected_blocks};//Doing this the hacky way so I can add custom goodbye message
    print(player,format('gi Successfully redid '+moves+' operations, filling '+length(global_affected_blocks)+' blocks'));
    global_affected_blocks=[];
    _print(player, 'success_undo', moves, affected);
);

fill(block,replacement)->(
    player=player();
    [pos1,pos2]=_get_player_positions(player);
    volume(pos1,pos2,set_block(pos(_),block,replacement));

    add_to_history('fill', player)
);

rotate(centre, degrees, axis)->(
    player=player();
    [pos1,pos2]=_get_player_positions(player);

    rotation_map={};
    rotation_matrix=[];
    if( axis=='x',
        rotation_matrix=[
            [1,0,0],
            [0,cos(degrees),-sin(degrees)],
            [0,sin(degrees),cos(degrees)]
        ],
        axis=='y',
        rotation_matrix=[
            [cos(degrees),0,sin(degrees)],
            [0,1,0],
            [-sin(degrees),0,cos(degrees)]
        ],//axis=='z'
        rotation_matrix=[
            [cos(degrees),-sin(degrees),0],
            [sin(degrees),cos(degrees),0]
            [0,0,1],
        ]
    );

    volume(pos1,pos2,
        block=block(_);//todo rotating stairs etc.
        prev_pos=pos(_);
        subtracted_pos=prev_pos-centre;
        rotated_matrix=rotation_matrix*[subtracted_pos,subtracted_pos,subtracted_pos];//cos matrix multiplication dont work in scarpet, yet...
        new_pos=[];
        for(rotated_matrix,
            new_pos+=reduce(_,_a+_,0)
        );
        new_pos=new_pos+centre;
        put(rotation_map,new_pos,block)//not setting now cos still querying, could mess up and set block we wanted to query
    );

    for(rotation_map,
        set_block(_,rotation_map:_,null)
    );

    add_to_history('rotate', player)
);

clone(new_pos, move)->(
    player=player();
    [pos1,pos2]=_get_player_positions(player);

    min_pos=map(pos1,min(_,pos2:_i));
    clone_map={};
    translation_vector=new_pos-min_pos;

    volume(pos1,pos2,
        put(clone_map,pos(_)+translation_vector,block(_));//not setting now cos still querying, could mess up and set block we wanted to query
        if(move,set_block(pos(_),if(has(block_state(_),'waterlogged'),'water','air')),null)//check for waterlog
    );

    for(clone_map,
        set_block(_,clone_map:_,null)
    );

    add_to_history(if(move,'move','clone'), player)
);

stack(count,direction) -> (
    player=player();
    translation_vector = pos_offset([0,0,0],if(direction,direction,player~'facing'));
    [pos1,pos2]=_get_player_positions(player);

    translation_vector = translation_vector*map(pos1-pos2,abs(_)+1);

    loop(count,
        c = _;
        offset = translation_vector*(c+1);
        volume(pos1,pos2,
            set_block(pos(_)+offset,_,null);
        );
    );

    add_to_history('stack', player)
);


expand(centre, magnitude)->(
    player=player();
    [pos1,pos2]=_get_player_positions(player);
    expand_map={};
    min_pos=map(pos1,min(_,pos2:_i));
    max_pos=map(pos1,max(_,pos2:_i));


    step=max(1,magnitude)-1;
    volume(pos1,pos2,
        if(block(_)!='air',//cos that way shrinkage retains more blocks and less garbage
            put(expand_map,(pos(_)-centre)*(magnitude-1)+pos(_),block(_))
        )
    );

    for(expand_map,
        set_block(_,expand_map:_,null)
    );
    add_to_history('expand',player)
);
